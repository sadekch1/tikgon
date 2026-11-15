'use strict';
// ────────────────────────────────────────────────────────────────
// محرك TikGon (يعمل داخل nodejs-mobile) — يتواصل مع التطبيق عبر HTTP محلي
// argv: node main.js <dataDir> <port> <token>
// دوره: ربط رقم المستخدم (Baileys)، مراقبة محادثة واحدة يختارها المستخدم،
// تحميل كل فيديو يصل إليها من البوت إلى مجلد inbox، وبثّ الأحداث لكوتلن.
// المحرك لا يرسل أي رسالة واتساب أبداً — استقبال فقط.
// ────────────────────────────────────────────────────────────────
const os = require('os');
try { os.networkInterfaces(); } catch (_) { os.networkInterfaces = () => ({}); }

// ── ترقيعات توافق Node 18 (nodejs-mobile) مع Baileys ──
// nodejs-mobile مبني بدون ICU: خيار fatal/ignoreBOM في TextDecoder غير مدعوم، فنتجاهله بدل الانهيار
(function patchTextDecoder() {
    const util = require('util');
    try { new util.TextDecoder('utf-8', { fatal: true, ignoreBOM: true }); return; } catch (_) {}
    const Orig = util.TextDecoder;
    class SafeTextDecoder extends Orig {
        constructor(enc, opts) {
            const o = Object.assign({}, opts || {});
            delete o.fatal; delete o.ignoreBOM;
            super(enc, o);
        }
    }
    globalThis.TextDecoder = SafeTextDecoder;
    util.TextDecoder = SafeTextDecoder;
})();
if (!globalThis.crypto) globalThis.crypto = require('crypto').webcrypto;
if (!Promise.withResolvers) {
    Promise.withResolvers = function () {
        let resolve, reject;
        const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
        return { promise, resolve, reject };
    };
}
if (!Array.prototype.toSorted) {
    Object.defineProperty(Array.prototype, 'toSorted', {
        value: function (fn) { return [...this].sort(fn); }, configurable: true, writable: true
    });
}
if (!Array.prototype.toReversed) {
    Object.defineProperty(Array.prototype, 'toReversed', {
        value: function () { return [...this].reverse(); }, configurable: true, writable: true
    });
}
if (!Array.prototype.findLast) {
    Object.defineProperty(Array.prototype, 'findLast', {
        value: function (fn) { for (let i = this.length - 1; i >= 0; i--) if (fn(this[i], i, this)) return this[i]; },
        configurable: true, writable: true
    });
}
// أي خطأ غير ملتقط يُسجَّل ولا يُنهي العملية (إنهاء Node يقتل التطبيق كله)
process.exit = (code) => { try { console.error('process.exit(' + code + ') blocked'); } catch (_) {} };

const fs = require('fs');
const path = require('path');
const http = require('http');
let P = null, baileys = {}, loadError = null;
try {
    P = require('pino');
    baileys = require('@whiskeysockets/baileys');
} catch (e) {
    loadError = e;
}

const makeWASocket = baileys.makeWASocket || baileys.default;
const {
    useMultiFileAuthState,
    downloadContentFromMessage,
    fetchLatestBaileysVersion,
    DisconnectReason = {}
} = baileys;

const DATA_DIR = process.argv[2];
const PORT = parseInt(process.argv[3], 10);
const TOKEN = process.argv[4];

const AUTH_DIR = path.join(DATA_DIR, 'auth');
const STAGE_DIR = path.join(DATA_DIR, 'staging');
const INBOX_DIR = path.join(DATA_DIR, 'inbox');
const ERROR_LOG = path.join(DATA_DIR, 'node-errors.log');
fs.mkdirSync(STAGE_DIR, { recursive: true });
fs.mkdirSync(INBOX_DIR, { recursive: true });
fs.mkdirSync(path.join(DATA_DIR, 'tmp'), { recursive: true });
if (loadError) { try { fs.appendFileSync(path.join(DATA_DIR, 'node-errors.log'), `[load] ${loadError.stack || loadError}\n`); } catch (_) {} }
process.on('exit', (c) => { try { fs.appendFileSync(path.join(DATA_DIR, 'node-errors.log'), `[exit] code=${c}\n`); } catch (_) {} });
process.env.TMPDIR = path.join(DATA_DIR, 'tmp');

// ───────────────────────── الإعدادات ─────────────────────────
const MAX_ATTEMPTS = 5;
const RETRY_BASE_MS = 3000;
const RETRY_MAX_MS = 60000;
const STALL_MS = 25000;
const ATTEMPT_MAX_MS = 30 * 60 * 1000;   // 30 دقيقة لمحاولة واحدة (الملفات الكبيرة)
const RECONNECT_BASE = 2000;
const RECONNECT_MAX = 60000;
const BOOT_TOLERANCE = 10;
const PAIR_MAX_TRIES = 6;

const cfg = {
    targetJid: '',     // المحادثة المختارة (JID كامل) — الأساس في TikGon
    target: '',        // بديل بأسلوب WA Hub: أرقام رقم تُطابَق داخل أي JID
    gapMin: 1,         // فاصل بين التحميلات (ثوانٍ)
    gapMax: 3
};

const BOOT_TIME = Math.floor(Date.now() / 1000);

// ───────────────────────── أدوات ─────────────────────────
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rand = (a, b) => a + Math.floor(Math.random() * (b - a + 1));

function toNum(v) {
    if (v == null) return 0;
    if (typeof v === 'object' && typeof v.toNumber === 'function') return v.toNumber();
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
}

function logError(err) {
    try { fs.appendFileSync(ERROR_LOG, `[${new Date().toISOString()}] ${err && err.stack || err}\n`); } catch (_) {}
}

function safeUnlink(p) { try { if (fs.existsSync(p)) fs.unlinkSync(p); } catch (_) {} }

function freeBytes() {
    try {
        const s = fs.statfsSync(DATA_DIR);
        return Number(s.bavail) * Number(s.bsize);
    } catch (_) { return -1; }
}

function withTimeout(promise, ms, message) {
    let t;
    const timeout = new Promise((_, rej) => { t = setTimeout(() => rej(new Error(message)), ms); });
    return Promise.race([promise, timeout]).finally(() => clearTimeout(t));
}

function isExpiredError(err) {
    const code = err && err.output && err.output.statusCode || err && err.statusCode;
    const msg = String(err && err.message || '');
    return code === 404 || code === 410 || /\b(404|410)\b/.test(msg) || /empty media key|media key|expired/i.test(msg);
}

/** فكّ أغلفة الرسائل (مؤقتة/مشاهدة واحدة/مستند بتعليق) */
function unwrap(message) {
    let m = message;
    for (let i = 0; i < 4 && m; i++) {
        if (m.ephemeralMessage) m = m.ephemeralMessage.message;
        else if (m.viewOnceMessage) m = m.viewOnceMessage.message;
        else if (m.viewOnceMessageV2) m = m.viewOnceMessageV2.message;
        else if (m.documentWithCaptionMessage) m = m.documentWithCaptionMessage.message;
        else break;
    }
    return m || {};
}

function textOf(m) {
    return String(m.conversation || (m.extendedTextMessage && m.extendedTextMessage.text) || '').trim();
}

// ───────────────────────── علامات البوت (TikGon) ─────────────────────────
// بداية جلسة:  🎬 *TikGon feed:* <query> [<HD|SD>]
// فيديو:        تعليق أوله 🎞 *<n>* • <query>
// إيقاف:        🛑 *توقّفت خلاصة RedGIFs.* …
// نهاية:        🏁 *انتهت نتائج البحث* …
function parseSessionStart(text) {
    const first = String(text || '').split('\n')[0];
    if (!first.startsWith('🎬') || !first.includes('TikGon feed:')) return null;
    let rest = first.slice(first.indexOf('TikGon feed:') + 'TikGon feed:'.length).replace(/\*/g, '').trim();
    let quality = '';
    const qm = rest.match(/\[([^\]]+)\]\s*$/);
    if (qm) { quality = qm[1].trim(); rest = rest.slice(0, qm.index).trim(); }
    return { query: rest, quality };
}

function parseVideoCaption(caption) {
    const first = String(caption || '').split('\n')[0].replace(/\*/g, '').trim();
    const m = first.match(/(\d+)\s*•\s*(.+)$/);
    if (m) return { index: parseInt(m[1], 10) || 0, query: m[2].trim() };
    return { index: 0, query: '' };
}

// ───────────────────────── قناة الأحداث (SSE) ─────────────────────────
const clients = new Set();
let conn = 'idle';
let pairingCode = '';

function emit(obj) {
    const line = `data: ${JSON.stringify(obj)}\n\n`;
    for (const res of clients) { try { res.write(line); } catch (_) {} }
}
function log(msg) { emit({ t: 'log', msg: String(msg) }); }
function setConn(c) { conn = c; emit({ t: 'state', conn: c, registered: isRegistered() }); }

process.on('uncaughtException', (e) => { logError(e); log('خطأ غير متوقع: ' + e.message); });
process.on('unhandledRejection', (e) => { logError(e); log('رفض غير متوقع: ' + (e && e.message || e)); });

function isRegistered() {
    try {
        const creds = JSON.parse(fs.readFileSync(path.join(AUTH_DIR, 'creds.json'), 'utf8'));
        return !!creds.registered;
    } catch (_) { return false; }
}

// ───────────────────────── المهام والطابور ─────────────────────────
const jobs = new Map();       // id -> job
const queue = [];             // jobs منتظرة بالترتيب
const seenIds = new Set();
let running = false;
let counter = 0;

function pubJob(j) {
    return { id: j.id, name: j.name, size: j.size, from: j.from, status: j.status,
             done: j.done || 0, attempt: j.attempt || 0, error: j.error || '' };
}

function snapshot() {
    return { t: 'snapshot', conn, registered: isRegistered(), code: pairingCode,
             fatal: loadError ? `فشل تحميل مكتبة واتساب: ${loadError.message}` : '',
             jobs: [...jobs.values()].map(pubJob) };
}

function enqueue(job) {
    jobs.set(job.id, job);
    queue.push(job);
    emit({ t: 'queued', ...pubJob(job) });
    processQueue();
}

async function processQueue() {
    if (running) return;
    running = true;
    try {
        while (queue.length > 0) {
            const job = queue.shift();
            if (job.status === 'canceled') continue;
            await handleJob(job);
            if (queue.length > 0) {
                const lo = Math.max(0, cfg.gapMin), hi = Math.max(lo, cfg.gapMax);
                const gapMs = rand(lo * 1000, hi * 1000);
                const until = Date.now() + gapMs;
                while (Date.now() < until) await sleep(300);
            }
        }
    } finally {
        running = false;
    }
}

function downloadWithProgress(job, stream, partPath) {
    return new Promise((resolve, reject) => {
        const out = fs.createWriteStream(partPath);
        let downloaded = 0, settled = false, stallTimer = null;
        let lastEmit = 0, lastBytes = 0, lastTime = Date.now(), speed = 0;

        const resetStall = () => {
            clearTimeout(stallTimer);
            stallTimer = setTimeout(() => fail(new Error('توقف التنزيل (لا استجابة)')), STALL_MS);
        };
        const fail = (err) => {
            if (settled) return;
            settled = true;
            clearTimeout(stallTimer);
            job.abort = null;
            try { stream.destroy(); } catch (_) {}
            out.destroy();
            reject(err);
        };
        job.abort = () => fail(new Error('canceled'));

        resetStall();
        stream.on('data', (chunk) => {
            downloaded += chunk.length;
            job.done = downloaded;
            resetStall();
            const now = Date.now();
            if (now - lastEmit >= 300) {
                const dt = (now - lastTime) / 1000;
                if (dt > 0) {
                    const inst = (downloaded - lastBytes) / dt;
                    speed = speed ? speed * 0.6 + inst * 0.4 : inst;
                }
                lastBytes = downloaded; lastTime = now; lastEmit = now;
                emit({ t: 'progress', id: job.id, done: downloaded, total: job.size, speed: Math.round(speed) });
            }
        });
        stream.on('error', fail);
        out.on('error', fail);
        out.on('finish', () => {
            if (settled) return;
            settled = true;
            clearTimeout(stallTimer);
            job.abort = null;
            resolve(downloaded);
        });
        stream.pipe(out);
    });
}

async function handleJob(job) {
    const partPath = path.join(STAGE_DIR, `${job.id}.part`);
    const finalPath = path.join(INBOX_DIR, job.name);

    if (job.size > 0) {
        const free = freeBytes();
        if (free >= 0 && free < job.size * 1.1) {
            job.status = 'failed';
            job.error = 'مساحة غير كافية';
            emit({ t: 'fail', id: job.id, error: job.error, final: true });
            jobs.delete(job.id);
            return;
        }
    }
    if (fs.existsSync(finalPath)) {   // نُزّل سابقاً (إعادة تشغيل المحرك مثلاً)
        job.status = 'done';
        jobs.delete(job.id);
        emitVideo(job, finalPath, fs.statSync(finalPath).size);
        return;
    }

    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        if (job.status === 'canceled') { safeUnlink(partPath); return; }
        job.status = 'active';
        job.attempt = attempt;
        job.done = 0;
        emit({ t: 'start', id: job.id, attempt });

        try {
            const stream = await withTimeout(
                downloadContentFromMessage(job.media, job.mediaType),
                60000, 'انتهت مهلة بدء الاتصال بالملف'
            );
            await withTimeout(downloadWithProgress(job, stream, partPath), ATTEMPT_MAX_MS, 'انتهت مهلة التحميل');

            const actual = fs.statSync(partPath).size;
            if (actual === 0) throw new Error('الملف المحمّل فارغ');
            if (job.size > 0 && actual !== job.size) throw new Error(`الحجم غير مطابق (${actual} من ${job.size})`);

            fs.renameSync(partPath, finalPath);
            job.status = 'done';
            jobs.delete(job.id);
            emitVideo(job, finalPath, actual);
            return;
        } catch (err) {
            logError(err);
            safeUnlink(partPath);

            if (job.status === 'canceled' || (err && err.message === 'canceled')) {
                job.status = 'canceled';
                emit({ t: 'canceled', id: job.id });
                jobs.delete(job.id);
                return;
            }
            if (isExpiredError(err)) {
                job.status = 'expired';
                job.error = 'انتهت صلاحية الملف على خوادم واتساب';
                emit({ t: 'expired', id: job.id, error: job.error });
                jobs.delete(job.id);
                return;
            }
            job.error = err.message;
            if (attempt < MAX_ATTEMPTS) {
                emit({ t: 'fail', id: job.id, error: err.message, final: false, attempt });
                const wait = Math.min(RETRY_MAX_MS, RETRY_BASE_MS * 2 ** (attempt - 1)) + rand(0, 1500);
                const until = Date.now() + wait;
                while (Date.now() < until && job.status !== 'canceled') await sleep(300);
            }
        }
    }
    job.status = 'failed';
    emit({ t: 'fail', id: job.id, error: job.error || 'فشل', final: true });
    jobs.delete(job.id);
}

function emitVideo(job, filePath, size) {
    emit({
        t: 'video', path: filePath, name: job.name, size,
        caption: job.caption || '', query: job.query || '', index: job.index || 0,
        timestamp: job.msgTs || Date.now()
    });
}

// ───────────────────────── قائمة المحادثات (لشاشة الاختيار) ─────────────────────────
const chatMap = new Map();   // jid -> { jid, name, ts, preview }

function noteChat(jid, name, ts, preview) {
    if (!jid || jid === 'status@broadcast') return;
    const e = chatMap.get(jid) || { jid, name: '', ts: 0, preview: '' };
    if (name) e.name = name;
    if (ts && ts >= e.ts) { e.ts = ts; if (preview) e.preview = preview; }
    chatMap.set(jid, e);
}

async function listChats(sock) {
    const out = [];
    for (const e of chatMap.values()) {
        let name = e.name;
        if (!name && e.jid.endsWith('@g.us') && sock) {
            try {
                const md = await sock.groupMetadata(e.jid);
                if (md && md.subject) { name = md.subject; e.name = name; }
            } catch (_) {}
        }
        if (!name) name = e.jid.split('@')[0];
        out.push({ jid: e.jid, name, ts: e.ts, preview: e.preview || '' });
    }
    out.sort((a, b) => b.ts - a.ts);
    emit({ t: 'chats', chats: out.slice(0, 80) });
}

// ───────────────────────── واتساب ─────────────────────────
let currentSock = null;
let reconnectTimer = null;
let reconnectAttempts = 0;
let pairingPhone = null;
let pairTries = 0;
let stopReconnect = false;
let pairTimer = null;

function destroySock(s) {
    if (!s) return;
    for (const ev of ['connection.update', 'messages.upsert', 'creds.update', 'chats.upsert', 'chats.update', 'contacts.upsert', 'contacts.update']) {
        try { s.ev.removeAllListeners(ev); } catch (_) {}
    }
    try { s.end(undefined); } catch (_) {}
}

function scheduleReconnect(fast = false) {
    if (reconnectTimer || stopReconnect) return;
    reconnectAttempts++;
    const delayMs = fast ? 1000 : Math.min(RECONNECT_MAX, RECONNECT_BASE * 2 ** (reconnectAttempts - 1)) + rand(0, 1000);
    setConn('reconnecting');
    log(`إعادة الاتصال بعد ${(delayMs / 1000).toFixed(1)}ث (محاولة ${reconnectAttempts})`);
    reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        connect().catch((e) => { logError(e); scheduleReconnect(); });
    }, delayMs);
}

function matchesTarget(msg) {
    const jid = msg.key.remoteJid || '';
    if (cfg.targetJid) return jid === cfg.targetJid;
    if (!cfg.target) return false;   // لم تُختر محادثة بعد: لا تحميل من أي مكان
    if (msg.key.fromMe) return true;
    const jids = [msg.key.remoteJid, msg.key.remoteJidAlt, msg.key.participant, msg.key.participantAlt].filter(Boolean);
    if (jids.some((j) => j.includes(cfg.target))) return true;
    return jids.some((j) => j.endsWith('@lid'));
}

async function connect() {
    destroySock(currentSock);
    stopReconnect = false;
    setConn('connecting');

    const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
    let version;
    try { ({ version } = await fetchLatestBaileysVersion()); } catch (e) { logError(e); }

    const sock = makeWASocket({
        auth: state,
        ...(version ? { version } : {}),
        printQRInTerminal: false,
        browser: ['Ubuntu', 'Chrome', '20.0.04'],
        syncFullHistory: false,
        markOnlineOnConnect: false,
        connectTimeoutMs: 60000,
        keepAliveIntervalMs: 25000,
        logger: P({ level: 'silent' })
    });
    currentSock = sock;
    const isCurrent = () => sock === currentSock;

    sock.ev.on('creds.update', saveCreds);

    // تغذية قائمة المحادثات من أحداث Baileys الجانبية (للاختيار فقط — لا تحميل منها)
    try {
        sock.ev.on('chats.upsert', (chats) => {
            for (const c of chats || []) noteChat(c.id, c.name || '', toNum(c.conversationTimestamp), '');
        });
        sock.ev.on('chats.update', (chats) => {
            for (const c of chats || []) if (c.id) noteChat(c.id, c.name || '', toNum(c.conversationTimestamp), '');
        });
        sock.ev.on('contacts.upsert', (contacts) => {
            for (const c of contacts || []) if (c.id) noteChat(c.id, c.name || c.notify || '', 0, '');
        });
        sock.ev.on('contacts.update', (contacts) => {
            for (const c of contacts || []) if (c.id) noteChat(c.id, c.name || c.notify || '', 0, '');
        });
    } catch (_) {}

    let codeRequested = false;
    async function requestCode() {
        if (codeRequested || !pairingPhone || sock.authState.creds.registered) return;
        codeRequested = true;
        try {
            await sleep(1500);
            if (!isCurrent()) return;
            const code = await sock.requestPairingCode(pairingPhone);
            pairingCode = code && code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : String(code);
            clearTimeout(pairTimer);
            emit({ t: 'code', code: pairingCode });
            log('تم الحصول على رمز الربط — أدخله في واتساب');
            setConn('pairing');
        } catch (err) {
            logError(err);
            pairTries++;
            log(`فشل طلب الرمز (${pairTries}/${PAIR_MAX_TRIES}): ${err.message}`);
            if (pairTries >= PAIR_MAX_TRIES) {
                pairingPhone = null;
                emit({ t: 'error', msg: 'تعذّر الحصول على رمز الربط. تحقق من الإنترنت والرقم ثم أعد المحاولة.' });
                setConn('needpair');
                return;
            }
            try { sock.end(undefined); } catch (_) {}
            if (isCurrent()) scheduleReconnect();
        }
    }

    sock.ev.on('connection.update', (update) => {
        if (!isCurrent()) return;
        const { connection, lastDisconnect, qr } = update;
        if (qr) { log('وصل الاتصال بواتساب — جارٍ طلب رمز الربط'); requestCode(); }

        if (connection === 'open') {
            reconnectAttempts = 0;
            pairingPhone = null;
            pairingCode = '';
            setConn('open');
        }

        if (connection === 'close') {
            const code = lastDisconnect && lastDisconnect.error && lastDisconnect.error.output
                ? lastDisconnect.error.output.statusCode : undefined;
            log(`انقطع الاتصال: code=${code} ${(lastDisconnect && lastDisconnect.error && lastDisconnect.error.message) || ''}`);

            if (code === DisconnectReason.loggedOut) {
                try { fs.rmSync(AUTH_DIR, { recursive: true, force: true }); } catch (_) {}
                if (pairingPhone && pairTries < PAIR_MAX_TRIES) {
                    pairTries++;
                    log(`لم يُقبل الرمز أو انتهت صلاحيته (${pairTries}/${PAIR_MAX_TRIES}) — رمز جديد...`);
                    scheduleReconnect(true);
                    return;
                }
                pairingPhone = null;
                setConn('loggedout');
                return;
            }
            if (code === DisconnectReason.connectionReplaced) {
                stopReconnect = true;
                setConn('replaced');
                return;
            }
            scheduleReconnect(code === DisconnectReason.restartRequired);
        }
    });

    sock.ev.on('messages.upsert', ({ messages, type }) => {
        if (!isCurrent() || type !== 'notify') return;
        const sorted = [...messages].sort((a, b) => toNum(a.messageTimestamp) - toNum(b.messageTimestamp));

        for (const msg of sorted) {
            if (!msg.message) continue;
            const jid = msg.key.remoteJid || '';
            const m = unwrap(msg.message);
            const text = textOf(m);
            const capForPreview = (m.videoMessage && m.videoMessage.caption) || (m.documentMessage && m.documentMessage.caption) || '';
            noteChat(jid, (!jid.endsWith('@g.us') && !msg.key.fromMe) ? (msg.pushName || '') : '', toNum(msg.messageTimestamp), text || capForPreview);

            const ts = toNum(msg.messageTimestamp);
            if (ts && ts < BOOT_TIME - BOOT_TOLERANCE) continue;
            if (!matchesTarget(msg)) continue;

            const mid = msg.key.id;
            if (mid) {
                if (seenIds.has(mid)) continue;
                seenIds.add(mid);
            }

            // ① علامات الجلسة النصية من البوت
            if (text) {
                const start = parseSessionStart(text);
                if (start) { emit({ t: 'session_start', query: start.query, quality: start.quality }); continue; }
                if (text.startsWith('🛑')) { emit({ t: 'session_end', query: '', reason: 'stopped' }); continue; }
                if (text.startsWith('🏁')) { emit({ t: 'session_end', query: '', reason: 'exhausted' }); continue; }
            }

            // ② الفيديوهات (رسالة فيديو، أو مستند فيديو كاحتياط)
            let media = null, mediaType = '';
            if (m.videoMessage) { media = m.videoMessage; mediaType = 'video'; }
            else if (m.documentMessage && String(m.documentMessage.mimetype || '').startsWith('video/')) {
                media = m.documentMessage; mediaType = 'document';
            }
            if (!media) continue;

            const caption = String(media.caption || '');
            const parsed = parseVideoCaption(caption);
            const msgTs = (ts || Math.floor(Date.now() / 1000)) * 1000;
            const safeId = String(mid || `${Date.now().toString(36)}${counter}`).replace(/[^A-Za-z0-9_-]/g, '');
            const job = {
                id: `${Date.now().toString(36)}${(counter++).toString(36)}`,
                media, mediaType,
                name: `${msgTs}_${safeId}.mp4`,
                size: toNum(media.fileLength),
                mime: media.mimetype || 'video/mp4',
                from: jid.split('@')[0],
                caption, query: parsed.query, index: parsed.index, msgTs,
                status: 'queued', done: 0, attempt: 0, error: ''
            };
            enqueue(job);
        }
    });
}

// ───────────────────────── فحص الاتصال ─────────────────────────
async function runDiag() {
    log(`— فحص —  Node ${process.version} ${process.platform}/${process.arch}`);
    log('الوقت: ' + new Date().toISOString());
    const dns = require('dns');
    try {
        const addr = await new Promise((res, rej) => dns.lookup('web.whatsapp.com', (e, a) => (e ? rej(e) : res(a))));
        log('DNS ✓ ' + addr);
    } catch (e) { log('DNS ✗ ' + (e.code || '') + ' ' + e.message); }

    try {
        await new Promise((res, rej) => {
            const tls = require('tls');
            const sock = tls.connect({ host: 'web.whatsapp.com', port: 443, servername: 'web.whatsapp.com' }, () => {
                log(`TLS ✓ ${sock.getProtocol()} authorized=${sock.authorized}${sock.authorizationError ? ' ' + sock.authorizationError : ''}`);
                sock.end(); res();
            });
            sock.on('error', rej);
            sock.setTimeout(10000, () => sock.destroy(new Error('timeout')));
        });
    } catch (e) { log('TLS ✗ ' + e.message); }

    try {
        const v = await withTimeout(fetchLatestBaileysVersion(), 15000, 'timeout');
        log('نسخة واتساب: ' + JSON.stringify(v).slice(0, 160));
    } catch (e) { log('جلب النسخة ✗ ' + e.message); }
    log('— انتهى الفحص —');
}

// ───────────────────────── الأوامر من التطبيق ─────────────────────────
async function handleCmd(c) {
    if (loadError && ['connect', 'pair', 'diag'].includes(c.cmd)) {
        emit({ t: 'error', msg: `فشل تحميل مكتبة واتساب: ${loadError.message}` });
        return;
    }
    switch (c.cmd) {
        case 'connect':
            if (currentSock) { emit(snapshot()); break; }
            if (isRegistered()) await connect();
            else setConn('needpair');
            break;

        case 'pair': {
            const phone = String(c.phone || '').replace(/[^0-9]/g, '');
            if (phone.length < 8) { emit({ t: 'error', msg: 'رقم غير صالح' }); break; }
            destroySock(currentSock); currentSock = null;
            clearTimeout(reconnectTimer); reconnectTimer = null;
            try { fs.rmSync(AUTH_DIR, { recursive: true, force: true }); } catch (_) {}
            pairingPhone = phone; pairTries = 0; pairingCode = ''; reconnectAttempts = 0;
            clearTimeout(pairTimer);
            pairTimer = setTimeout(() => {
                if (pairingPhone && !pairingCode) {
                    emit({ t: 'error', msg: 'لم يتم الاتصال بواتساب خلال 40 ثانية. اضغط «فحص الاتصال» لمعرفة السبب.' });
                }
            }, 40000);
            log('بدء الربط…');
            await connect();
            break;
        }

        case 'config':
            if (c.targetJid !== undefined) cfg.targetJid = String(c.targetJid || '').trim();
            if (c.target !== undefined) cfg.target = String(c.target || '').replace(/[^0-9]/g, '');
            if (c.gapMin !== undefined) cfg.gapMin = Number(c.gapMin) || 0;
            if (c.gapMax !== undefined) cfg.gapMax = Number(c.gapMax) || 0;
            break;

        case 'setTarget':
            cfg.targetJid = String(c.jid || '').trim();
            cfg.target = '';
            log(cfg.targetJid ? `المحادثة المراقبة: ${cfg.targetJid}` : 'أُلغيت المحادثة المراقبة');
            break;

        case 'listChats':
            await listChats(currentSock);
            break;

        case 'diag':
            await runDiag();
            break;

        case 'logout':
            try { if (currentSock) await currentSock.logout(); } catch (_) {}
            destroySock(currentSock); currentSock = null;
            clearTimeout(reconnectTimer); reconnectTimer = null;
            try { fs.rmSync(AUTH_DIR, { recursive: true, force: true }); } catch (_) {}
            pairingPhone = null; pairingCode = '';
            setConn('needpair');
            break;

        default: break;
    }
}

// ───────────────────────── خادم HTTP المحلي ─────────────────────────
const server = http.createServer((req, res) => {
    if (req.headers['x-token'] !== TOKEN) { res.writeHead(403); return res.end(); }

    if (req.method === 'GET' && req.url === '/events') {
        res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
        res.write(':ok\n\n');
        clients.add(res);
        req.on('close', () => clients.delete(res));
        res.write(`data: ${JSON.stringify(snapshot())}\n\n`);
        return;
    }

    if (req.method === 'POST' && req.url === '/cmd') {
        let body = '';
        req.on('data', (d) => { body += d; if (body.length > 1e6) req.destroy(); });
        req.on('end', () => {
            res.writeHead(200); res.end('ok');
            try { handleCmd(JSON.parse(body)).catch((e) => { logError(e); log('خطأ أمر: ' + e.message); }); }
            catch (e) { logError(e); }
        });
        return;
    }
    res.writeHead(404); res.end();
});
server.listen(PORT, '127.0.0.1', () => log('محرك TikGon جاهز'));
setInterval(() => { for (const r of clients) { try { r.write(':hb\n\n'); } catch (_) {} } }, 15000);

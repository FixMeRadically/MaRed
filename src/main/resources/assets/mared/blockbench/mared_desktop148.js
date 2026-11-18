/* MaRed desktop connector. Load this file through Blockbench > Plugins > Load from file. */
(function () {
    'use strict';
    const Buffer = require('buffer').Buffer;
    let action, socket, timer, heartbeat, session = '', projectId = '', loaded = false;
    let dirty = false, saving = false, lastText = '', inflight = '', saveTimer, incoming = '', sequence = 0, bound = null, writable = false;
    const LIMIT = 4 * 1024 * 1024, MAX_WIRE = 26 * 1024 * 1024;
    const hint = message => Blockbench.showQuickMessage('MaRed: ' + message, 5000);
    function send(message) { if (socket && !socket.destroyed) socket.write(JSON.stringify(message) + '\n'); }
    function compile() {
        if (!Project || Project !== bound) return null;
        let text = Codecs.project.compile({bitmaps: true});
        if (typeof text !== 'string') text = JSON.stringify(text);
        if (Buffer.byteLength(text, 'utf8') > LIMIT) throw new Error('Model exceeds 4 MiB');
        return text;
    }
    function flush() {
        clearTimeout(timer);
        if (!dirty || saving || !loaded || !writable) return;
        try { const text = compile(); if (text === null) return;
            if (text === lastText) { dirty = false; return; }
            saving = true; inflight = text; clearTimeout(saveTimer);saveTimer = setTimeout(() => { if (saving) { saving = false; loaded = false; send({type: 'conflict', session}); hint('No save acknowledgment. Keep a local copy and reconnect.'); } }, 60000);send({type: 'save', session, sequence: ++sequence, content: text});
        } catch (e) { hint(e.message); }
    }
    function changed() { if (loaded && Project === bound) { dirty = true; clearTimeout(timer); timer = setTimeout(flush, 250); } }
    function disconnect() {
        clearTimeout(timer); clearTimeout(saveTimer); clearInterval(heartbeat);
        loaded = false; saving = false; session = ''; incoming = ''; inflight = '';
        if (socket) { const old = socket; socket = null; old.destroy(); }
    }
    function message(data) {
        if (data.type === 'load') {
            // Recheck actual content: Undo and edits without finish_edit must not mislead the dirty flag.
            if (loaded && Project === bound && !saving) dirty = compile() !== lastText;
            if (Project && Project === bound && (dirty || saving || !loaded) && compile() === data.content) { clearTimeout(saveTimer); saving = false; dirty = false; lastText = data.content; inflight = ''; }
            if (loaded && (dirty || saving || Project !== bound)) { send({type: 'conflict', session}); hint('Remote changes waiting; save a copy of local work, then reconnect.'); return; }
            if (!loaded && Project && Project.saved === false && !(Project === bound && compile() === lastText)) { send({type: 'conflict', session: data.session}); hint('Save your current Blockbench project, then reconnect.'); return; }
            const model = JSON.parse(data.content);
            if (!model || !model.meta || typeof model.meta !== 'object') throw new Error('Invalid bbmodel');
            const previous = loaded || Project === bound && compile() === lastText ? bound : null;
            Codecs.project.load(model, {path: data.path, no_file: true});
            bound = Project; projectId = String(Project.uuid); session = data.session;
            if (previous && previous !== bound && typeof previous.close === 'function') { previous.saved = true; previous.close(); }
            if (Project !== bound && typeof bound.select === 'function') bound.select();
            loaded = true; writable = !!data.editable; dirty = false; saving = false; lastText = compile();
            // Do not mark the desktop project saved: the user's local save/recovery remains available.
            send({type: 'loaded', session, protocol149: true}); hint('Connected: ' + data.path + (data.editable ? '' : ' (read only)'));
        } else if (data.session === session && data.type === 'rights') { writable = !!data.editable; hint(writable ? 'Editing allowed' : 'Read only; local edits preserved'); if (writable && dirty) timer = setTimeout(flush, 50);
        } else if (data.session === session && data.type === 'saved' && saving && loaded && data.sequence === sequence) {
            clearTimeout(saveTimer); saving = false; lastText = typeof data.content === 'string' ? data.content : inflight; inflight = '';
            dirty = compile() !== lastText; if (dirty) timer = setTimeout(flush, 50);
        } else if (data.session === session && data.type === 'error') {
            clearTimeout(saveTimer); saving = false; hint(data.message || 'Synchronization stopped; local edits preserved.');
            // Conflict requires an explicit reconnect; never resend over a newer server baseline.
            loaded = false;
        }
    }
    function connect(pairing) {
        const match = /^127\.0\.0\.1:(\d{1,5})\/([a-f0-9]{64})$/.exec(String(pairing).trim());
        if (!match || +match[1] < 1 || +match[1] > 65535) return hint('Copy the connection code from MaRed.');
        disconnect();
        let net; try { net = require('net', {optional: false, message: 'Connect only to MaRed on this computer (127.0.0.1).'}); if (!net) throw new Error('Network permission denied'); socket = net.createConnection({host: '127.0.0.1', port: +match[1]}); } catch (e) { hint(e.message); return; }
        const peer = socket; peer.setEncoding('utf8'); peer.setNoDelay(true);
        peer.on('connect', () => { if (socket !== peer) { peer.destroy(); return; } peer.write('MARED148 ' + match[2] + '\n'); heartbeat = setInterval(() => send({type: 'ping'}), 5000); });
        peer.on('data', chunk => {
            if (socket !== peer) return;
            incoming += chunk;
            if (Buffer.byteLength(incoming, 'utf8') > MAX_WIRE) { hint('Message too large'); disconnect(); return; }
            let end; while ((end = incoming.indexOf('\n')) !== -1) {
                const line = incoming.slice(0, end); incoming = incoming.slice(end + 1);
                try { message(JSON.parse(line)); } catch (e) { hint(e.message); disconnect(); return; }
            }
        });
        peer.on('error', () => { if (socket === peer) { hint('Connection failed; check MaRed and reconnect.'); disconnect(); } });
        peer.on('close', () => { if (socket === peer) { hint('Disconnected. Your local model remains open.'); disconnect(); } });
    }
    Plugin.register('mared_desktop148', {
        title: 'MaRed Desktop Bridge', author: 'MaRed', version: '1.0.0', variant: 'desktop',
        description: 'Connects the current model to a MaRed project through authenticated localhost TCP.',
        onload() {
            action = new Action('mared_connect148', {name: 'Connect to MaRed', icon: 'sync', click() {
                new Dialog({id: 'mared_pair148', title: 'Connect to MaRed',
                    form: {pairing: {label: 'Connection code from MaRed', type: 'text'}},
                    onConfirm(form) { this.hide(); connect(form.pairing); }}).show();
            }});
            MenuBar.addAction(action, 'tools');
            Blockbench.on('finish_edit', changed); Blockbench.on('undo', changed); Blockbench.on('redo', changed);
        },
        onunload() {
            disconnect(); if (action) action.delete();
            Blockbench.removeListener('finish_edit', changed); Blockbench.removeListener('undo', changed); Blockbench.removeListener('redo', changed);
        }
    });
}());

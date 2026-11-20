const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function fixture(script, responder, hash = '') {
  const elements = new Map();
  const storage = new Map([['anotaai.termosCadastroAceitos', JSON.stringify({ versao: '2026-09-25' })]]);
  const calls = [];
  const get = id => {
    if (!elements.has(id)) elements.set(id, {
      hidden: false, value: '', disabled: false, textContent: '', listeners: {},
      addEventListener(event, fn) { this.listeners[event] = fn; }, setCustomValidity() {}
    });
    return elements.get(id);
  };
  get('verification-pending').hidden = true;
  const window = { location: { hash, pathname: '/pages/verificar-email.html', search: '',
    replace(url) { this.destination = url; } }, history: { replaceState(...args) { this.last = args; } } };
  const sessionStorage = { getItem: k => storage.get(k) || null, setItem: (k,v) => storage.set(k,v), removeItem: k => storage.delete(k) };
  const context = { document: { getElementById: get }, window, sessionStorage, URLSearchParams, Date, TypeError,
    fetch: async (url, options) => {
      calls.push({ url, options });
      const response = await responder(url, options);
      return { ok: true, status: 200, json: async () => response, ...response };
    } };
  const ready = vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', script), 'utf8'), context);
  return { get, storage, calls, window, ready };
}

const defaults = url => url === '/api/termos/atuais' ? { versao: '2026-09-25' }
  : url === '/api/auth/csrf' ? { headerName: 'X-XSRF-TOKEN', token: 'csrf' }
  : { desafioId: 'desafio-publico', expiraEmSegundos: 600 };

test('cadastro aguarda o e-mail sem redirecionar ao login nem armazenar a senha', async () => {
  const f = fixture('cadastro.js', defaults);
  await f.ready;
  f.get('senha').value = f.get('confirmar-senha').value = 'Conta!Forte2026';
  f.get('data-nascimento').value = '2000-01-01';
  f.get('nome').value = 'Estudante';
  f.get('email').value = 'pessoa@example.com';
  f.get('escolaridade').value = 'Ensino Superior Completo';
  await f.get('register-form').listeners.submit({ preventDefault() {} });
  assert.equal(f.get('register-form').hidden, true);
  assert.equal(f.get('verification-pending').hidden, false);
  assert.equal(f.window.location.destination, undefined);
  assert.equal(f.get('senha').value, '');
  assert.equal(f.get('confirmar-senha').value, '');
  const pending = JSON.parse(f.storage.get('anotaai.cadastroPendente'));
  assert.equal(pending.desafioId, 'desafio-publico');
  assert.equal(JSON.stringify([...f.storage]).includes('Conta!Forte2026'), false);
  await f.get('resend-email').listeners.click();
  assert.equal(f.calls.at(-1).url, '/api/auth/cadastro/reenviar');
  assert.equal(f.calls.at(-1).options.headers['X-XSRF-TOKEN'], 'csrf');
});

test('clique no link confirma automaticamente com token e CSRF', async () => {
  const secret = 'a'.repeat(43);
  const f = fixture('verificar-email.js', defaults, '#token=' + secret);
  await f.ready;
  assert.equal(f.calls.at(-1).url, '/api/auth/cadastro/confirmar');
  assert.equal(JSON.parse(f.calls.at(-1).options.body).token, secret);
  assert.equal(f.calls.at(-1).options.headers['X-XSRF-TOKEN'], 'csrf');
  assert.equal(f.get('verification-title').textContent, 'E-mail confirmado!');
  assert.equal(f.get('verification-next').href, 'login.html?cadastro=sucesso');
  assert.equal(f.window.history.last[2], '/pages/verificar-email.html');
  assert.equal(f.storage.has('anotaai.termosCadastroAceitos'), false);
});

test('link inválido não envia confirmação', async () => {
  const f = fixture('verificar-email.js', defaults, '#token=invalido');
  await f.ready;
  assert.equal(f.calls.length, 0);
  assert.equal(f.get('verification-title').textContent, 'Link de confirmação inválido');
});

test('link expirado mostra erro e caminho para novo cadastro', async () => {
  const f = fixture('verificar-email.js', url => url === '/api/auth/csrf' ? defaults(url)
    : { ok: false, status: 400, mensagem: 'Verificação expirada.' }, '#token=' + 'b'.repeat(43));
  await f.ready;
  assert.equal(f.get('verification-message').textContent, 'Verificação expirada.');
  assert.equal(f.get('verification-next').hidden, false);
  assert.equal(f.get('verification-retry').hidden, true);
});

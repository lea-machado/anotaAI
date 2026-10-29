(async function () {
  const loginForm = document.getElementById('login-form');
  const twoFactorForm = document.getElementById('two-factor-form');
  const loginFeedback = document.getElementById('login-feedback');
  const twoFactorFeedback = document.getElementById('two-factor-feedback');
  const loginButton = document.getElementById('login-button');
  const verifyButton = document.getElementById('verify-button');
  const codeInput = document.getElementById('verification-code');
  let challengeId = null;
  let countdownTimer = null;

  try {
    const current = await fetch('/api/auth/me', { credentials: 'same-origin' });
    if (current.ok) redirectAuthenticated(await current.json());
  } catch (_) {
    // A tela permanece disponivel enquanto o servidor inicia.
  }

  const params = new URLSearchParams(location.search);
  if (params.has('expirada')) showFeedback(loginFeedback, 'Sua sessão expirou. Entre novamente.');
  if (params.get('cadastro') === 'sucesso') {
    showFeedback(loginFeedback, 'Conta criada com sucesso. Agora entre com seu e-mail e senha.', 'success');
  }

  loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    loginFeedback.hidden = true;
    setBusy(loginButton, true, 'Enviando código...');

    try {
      const response = await secureRequest('/api/auth/login', {
        email: document.getElementById('email').value,
        senha: document.getElementById('senha').value
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.mensagem || 'Não foi possível entrar.');
      if (!data.doisFatoresNecessario) throw new Error('O servidor não iniciou a verificação em duas etapas.');

      challengeId = data.desafioId;
      showTwoFactorStep(data.emailMascarado, data.expiraEmSegundos);
    } catch (error) {
      showFeedback(loginFeedback, error.message);
      setBusy(loginButton, false, 'Entrar');
    }
  });

  twoFactorForm.addEventListener('submit', async event => {
    event.preventDefault();
    twoFactorFeedback.hidden = true;
    setBusy(verifyButton, true, 'Verificando...');

    try {
      const response = await secureRequest('/api/auth/2fa/verify', {
        desafioId: challengeId,
        codigo: codeInput.value
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.mensagem || 'Código inválido.');
      const user = await fetch('/api/auth/me', { credentials: 'same-origin' }).then(result => result.json());
      redirectAuthenticated(user);
    } catch (error) {
      showFeedback(twoFactorFeedback, error.message);
      setBusy(verifyButton, false, 'Verificar código');
      codeInput.select();
    }
  });

  document.getElementById('back-to-login').addEventListener('click', () => {
    clearInterval(countdownTimer);
    challengeId = null;
    codeInput.value = '';
    twoFactorForm.hidden = true;
    loginForm.hidden = false;
    document.getElementById('login-links').hidden = false;
    document.getElementById('auth-title').textContent = 'Acessar conta';
    document.getElementById('auth-description').textContent = 'Use seu e-mail e senha cadastrados para entrar no AnotaAI.';
    setBusy(loginButton, false, 'Entrar');
  });

  codeInput.addEventListener('input', () => {
    codeInput.value = codeInput.value.replace(/\D/g, '').slice(0, 6);
  });

  async function secureRequest(url, body) {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!csrfResponse.ok) throw new Error('Não foi possível iniciar uma sessão segura.');
    const csrf = await csrfResponse.json();
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify(body)
    });
  }

  function showTwoFactorStep(maskedEmail, expiresInSeconds) {
    loginForm.hidden = true;
    twoFactorForm.hidden = false;
    document.getElementById('login-links').hidden = true;
    document.getElementById('auth-title').textContent = 'Verifique seu e-mail';
    document.getElementById('auth-description').textContent = `Enviamos um código de 6 dígitos para ${maskedEmail}.`;
    codeInput.focus();
    startCountdown(expiresInSeconds);
  }

  function startCountdown(totalSeconds) {
    const expiration = document.getElementById('code-expiration');
    let remaining = totalSeconds;
    const render = () => {
      const minutes = Math.floor(remaining / 60);
      const seconds = String(remaining % 60).padStart(2, '0');
      expiration.textContent = remaining > 0
        ? `O código expira em ${minutes}:${seconds}.`
        : 'O código expirou. Volte e faça o login novamente.';
      remaining = Math.max(0, remaining - 1);
    };
    render();
    clearInterval(countdownTimer);
    countdownTimer = setInterval(render, 1000);
  }

  function setBusy(button, busy, label) {
    button.disabled = busy;
    button.textContent = label;
  }

  function showFeedback(element, message, type = 'error') {
    element.textContent = message;
    element.classList.toggle('success', type === 'success');
    element.hidden = false;
  }

  function redirectAuthenticated(user) {
    window.location.replace(user.perfil === 'ADMIN' ? '/pages/admin.html' : '/pages/anotacoes.html');
  }
})();

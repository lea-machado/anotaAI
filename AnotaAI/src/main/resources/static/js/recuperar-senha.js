(() => {
  const requestForm = document.getElementById('request-form');
  const resetForm = document.getElementById('reset-form');
  const requestButton = document.getElementById('request-button');
  const resetButton = document.getElementById('reset-button');
  const resendButton = document.getElementById('resend-button');
  const feedback = document.getElementById('recovery-feedback');
  const code = document.getElementById('recovery-code');
  let challengeId = null;
  let cooldown;
  let resendAt = 0;

  function updateResend() {
    const remaining = Math.max(0, Math.ceil((resendAt - Date.now()) / 1000));
    resendButton.disabled = remaining > 0 || resetButton.disabled;
    resendButton.textContent = remaining > 0 ? `Solicitar outro código (${remaining}s)` : 'Solicitar outro código';
    if (remaining === 0) clearInterval(cooldown);
  }

  function showFeedback(message, success = false) {
    feedback.textContent = message;
    feedback.classList.toggle('success', success);
    feedback.hidden = false;
  }

  async function post(path, body) {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!csrfResponse.ok) throw new Error('Não foi possível iniciar uma sessão segura. Tente novamente.');
    const csrf = await csrfResponse.json();
    const response = await fetch(`/api/auth/recuperacao/${path}`, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.mensagem || 'Não foi possível concluir. Tente novamente.');
    return data;
  }

  requestForm.addEventListener('submit', async event => {
    event.preventDefault();
    feedback.hidden = true;
    requestButton.disabled = true;
    requestButton.textContent = 'Enviando...';
    try {
      const data = await post('solicitar', { email: document.getElementById('recovery-email').value });
      challengeId = data.desafioId;
      requestForm.hidden = true;
      resetForm.hidden = false;
      document.getElementById('recovery-title').textContent = 'Crie uma nova senha';
      document.getElementById('recovery-description').textContent = data.mensagem + ' Confira também a pasta de spam.';
      code.focus();
      resendAt = Date.now() + 60000;
      clearInterval(cooldown);
      updateResend();
      cooldown = setInterval(updateResend, 1000);
    } catch (error) { showFeedback(error.message); }
    finally { requestButton.disabled = false; requestButton.textContent = 'Enviar código'; }
  });

  resetForm.addEventListener('submit', async event => {
    event.preventDefault();
    const password = document.getElementById('new-password').value;
    if (password !== document.getElementById('confirm-password').value) {
      showFeedback('As senhas não coincidem.');
      return;
    }
    if (new TextEncoder().encode(password).length > 72) {
      showFeedback('A senha é muito longa. Use no máximo 72 bytes.');
      return;
    }
    feedback.hidden = true;
    resetButton.disabled = true;
    resetButton.textContent = 'Salvando...';
    resendButton.disabled = true;
    try {
      const data = await post('redefinir', { desafioId: challengeId, codigo: code.value, novaSenha: password });
      challengeId = null;
      clearInterval(cooldown);
      resetForm.reset();
      resetForm.hidden = true;
      document.getElementById('recovery-title').textContent = 'Senha alterada';
      document.getElementById('recovery-description').textContent = 'Você já pode voltar ao login e entrar com sua nova senha.';
      showFeedback(data.mensagem, true);
    } catch (error) {
      showFeedback(error.message);
    } finally {
      resetButton.disabled = false;
      resetButton.textContent = 'Salvar nova senha';
      updateResend();
    }
  });

  resendButton.addEventListener('click', () => {
    clearInterval(cooldown);
    challengeId = null;
    resetForm.reset();
    resetForm.hidden = true;
    requestForm.hidden = false;
    feedback.hidden = true;
    document.getElementById('recovery-title').textContent = 'Esqueci minha senha';
    document.getElementById('recovery-description').textContent = 'Confirme seu e-mail para solicitar um novo código.';
    document.getElementById('recovery-email').focus();
  });
  code.addEventListener('input', () => { code.value = code.value.replace(/\D/g, '').slice(0, 6); });
})();

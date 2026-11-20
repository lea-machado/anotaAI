(async function () {
  const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
  window.history.replaceState(null, '', window.location.pathname);
  const title = document.getElementById('verification-title');
  const message = document.getElementById('verification-message');
  const next = document.getElementById('verification-next');
  const retry = document.getElementById('verification-retry');
  retry.addEventListener('click', confirm);
  await confirm();

  async function confirm() {
    retry.hidden = true;
    next.hidden = true;
    title.textContent = 'Confirmando seu e-mail...';
    if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) {
      title.textContent = 'Link de confirmação inválido';
      message.textContent = 'Abra o link enviado para seu e-mail ou preencha o cadastro novamente.';
      next.hidden = false;
      return;
    }
    try {
      const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
      if (!csrfResponse.ok) throw new Error('Não foi possível iniciar a confirmação. Tente novamente.');
      const csrf = await csrfResponse.json();
      const response = await fetch('/api/auth/cadastro/confirmar', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ token })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        retry.hidden = response.status < 500;
        throw new Error(data.mensagem || 'Não foi possível confirmar seu e-mail. Tente novamente.');
      }
      try {
        sessionStorage.removeItem('anotaai.cadastroPendente');
        sessionStorage.removeItem('anotaai.termosCadastroAceitos');
      } catch (_) { /* A conta já foi confirmada mesmo sem acesso ao armazenamento local. */ }
      title.textContent = 'E-mail confirmado!';
      message.textContent = 'Sua conta foi criada com sucesso. Você já pode entrar no AnotaAI.';
      next.href = 'login.html?cadastro=sucesso';
      next.textContent = 'Entrar na minha conta';
    } catch (error) {
      title.textContent = 'Não foi possível confirmar';
      message.textContent = error.message;
      if (error instanceof TypeError) retry.hidden = false;
    }
    next.hidden = false;
  }
})();

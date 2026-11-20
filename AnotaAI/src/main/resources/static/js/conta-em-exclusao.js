(async function () {
  const message = document.getElementById('deletion-message');
  const feedback = document.getElementById('recovery-feedback');
  const recoverButton = document.getElementById('recover-account');
  const leaveButton = document.getElementById('leave-platform');
  const recoveryDialog = document.getElementById('recover-account-dialog');
  const confirmRecoveryButton = document.getElementById('confirm-recover-account');
  const cancelRecoveryButton = document.getElementById('cancel-recover-account');
  const recoveryError = document.getElementById('recover-dialog-error');

  try {
    const response = await fetch('/api/auth/exclusao/status', { credentials: 'same-origin' });
    if (!response.ok) {
      window.location.replace('/pages/login.html');
      return;
    }
    const data = await response.json();
    const date = new Date(data.exclusaoEm);
    message.textContent = `Você solicitou a exclusão da sua conta. Seus dados serão excluídos em ${date.toLocaleDateString('pt-BR')}. Até lá, sua conta permanece suspensa e você pode recuperá-la.`;
    recoverButton.disabled = false;
  } catch (_) {
    message.textContent = 'Não foi possível consultar sua solicitação. Entre novamente.';
  }

  recoverButton.addEventListener('click', () => {
    if (recoverButton.disabled || recoveryDialog.open) return;
    recoveryError.textContent = '';
    recoveryDialog.showModal();
  });

  cancelRecoveryButton.addEventListener('click', () => recoveryDialog.close());
  recoveryDialog.addEventListener('cancel', event => {
    if (confirmRecoveryButton.disabled) event.preventDefault();
  });

  confirmRecoveryButton.addEventListener('click', async () => {
    if (confirmRecoveryButton.disabled || !recoveryDialog.open) return;
    confirmRecoveryButton.disabled = true;
    cancelRecoveryButton.disabled = true;
    recoverButton.disabled = true;
    confirmRecoveryButton.textContent = 'Recuperando...';
    recoveryError.textContent = '';
    try {
      const response = await securePost('/api/auth/exclusao/recuperar');
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.mensagem || 'Não foi possível recuperar a conta.');
      window.location.replace('/pages/login.html?recuperada=1');
    } catch (error) {
      recoveryError.textContent = error.message;
      recoverButton.disabled = false;
    } finally {
      confirmRecoveryButton.disabled = false;
      cancelRecoveryButton.disabled = false;
      confirmRecoveryButton.textContent = 'Sim, recuperar conta';
    }
  });

  leaveButton.addEventListener('click', async () => {
    leaveButton.disabled = true;
    try { await securePost('/api/auth/logout'); } catch (_) { }
    window.location.replace('/pages/login.html');
  });

  async function securePost(url) {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Não foi possível verificar a segurança da sessão.');
    const csrf = await response.json();
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { [csrf.headerName]: csrf.token }
    });
  }
})();
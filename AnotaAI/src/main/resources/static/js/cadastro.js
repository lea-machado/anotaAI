(function () {
  const TERMS_VERSION = '2026-09-25';
  const TERMS_ACCEPTANCE_KEY = 'anotaai.termosCadastroAceitos';

  if (sessionStorage.getItem(TERMS_ACCEPTANCE_KEY) !== TERMS_VERSION) {
    window.location.replace('/pages/privacidade.html?cadastro=1');
    return;
  }

  const form = document.getElementById('register-form');
  const feedback = document.getElementById('register-feedback');
  const button = document.getElementById('register-button');
  const birthDateInput = document.getElementById('data-nascimento');
  const today = new Date();
  const minimumBirthYear = today.getFullYear() - 18;
  const minimumBirthDay = Math.min(today.getDate(), new Date(minimumBirthYear, today.getMonth() + 1, 0).getDate());
  const maximumBirthDate = [minimumBirthYear, today.getMonth() + 1, minimumBirthDay]
    .map((part, index) => index === 0 ? String(part) : String(part).padStart(2, '0'))
    .join('-');
  birthDateInput.max = maximumBirthDate;

  form.addEventListener('submit', async event => {
    event.preventDefault();
    feedback.hidden = true;

    const senha = document.getElementById('senha').value;
    const confirmarSenha = document.getElementById('confirmar-senha').value;
    if (senha !== confirmarSenha) {
      showFeedback('As senhas não são iguais.');
      return;
    }
    if (!birthDateInput.value || birthDateInput.value > maximumBirthDate) {
      showFeedback('É necessário ter pelo menos 18 anos para criar uma conta.');
      return;
    }

    button.disabled = true;
    button.textContent = 'Criando conta...';

    try {
      const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
      const csrf = await csrfResponse.json();
      const response = await fetch('/api/auth/cadastro', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token
        },
        body: JSON.stringify({
          nome: document.getElementById('nome').value,
          email: document.getElementById('email').value,
          dataNascimento: birthDateInput.value,
          escolaridade: document.getElementById('escolaridade').value,
          senha,
          aceitouTermos: true,
          declarouMaioridade: true,
          versaoTermos: TERMS_VERSION
        })
      });

      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.mensagem || 'Não foi possível criar a conta.');
      sessionStorage.removeItem(TERMS_ACCEPTANCE_KEY);
      window.location.replace('/pages/login.html?cadastro=sucesso');
    } catch (error) {
      showFeedback(error.message);
      button.disabled = false;
      button.textContent = 'Criar conta';
    }
  });

  function showFeedback(message) {
    feedback.textContent = message;
    feedback.hidden = false;
  }
})();

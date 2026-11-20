(async function () {
  const TERMS_ACCEPTANCE_KEY = 'anotaai.termosCadastroAceitos';

  let accepted;
  try {
    accepted = JSON.parse(sessionStorage.getItem(TERMS_ACCEPTANCE_KEY) || 'null');
    const response = await fetch('/api/termos/atuais', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('Não foi possível consultar os termos.');
    const current = await response.json();
    if (!accepted || accepted.versao !== current.versao) {
      window.location.replace('/pages/privacidade.html?cadastro=1');
      return;
    }
  } catch (_) {
    window.location.replace('/pages/privacidade.html?cadastro=1');
    return;
  }

  const form = document.getElementById('register-form');
  const feedback = document.getElementById('register-feedback');
  const button = document.getElementById('register-button');
  const birthDateInput = document.getElementById('data-nascimento');
  const educationInput = document.getElementById('escolaridade');
  const today = new Date();
  const minimumBirthYear = today.getFullYear() - 18;
  const minimumBirthDay = Math.min(today.getDate(), new Date(minimumBirthYear, today.getMonth() + 1, 0).getDate());
  const maximumBirthDate = [minimumBirthYear, today.getMonth() + 1, minimumBirthDay]
    .map((part, index) => index === 0 ? String(part) : String(part).padStart(2, '0'))
    .join('-');
  birthDateInput.max = maximumBirthDate;

  educationInput.addEventListener('invalid', () => {
    educationInput.setCustomValidity('Selecione sua escolaridade para criar a conta.');
  });
  educationInput.addEventListener('change', () => educationInput.setCustomValidity(''));

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
          escolaridade: educationInput.value,
          senha,
          aceitouTermos: true,
          declarouMaioridade: true,
          versaoTermos: accepted.versao
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
})()
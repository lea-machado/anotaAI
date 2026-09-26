(function () {
  const TERMS_VERSION = '2026-09-25';
  const TERMS_ACCEPTANCE_KEY = 'anotaai.termosCadastroAceitos';
  const isRegistrationFlow = new URLSearchParams(window.location.search).get('cadastro') === '1';
  const acceptance = document.getElementById('legal-acceptance');

  if (!isRegistrationFlow || !acceptance) return;

  acceptance.hidden = false;
  const checkbox = document.getElementById('accept-terms');
  const adultCheckbox = document.getElementById('confirm-adult');
  const continueButton = document.getElementById('continue-registration');

  const updateButton = () => {
    continueButton.disabled = !checkbox.checked || !adultCheckbox.checked;
  };
  checkbox.addEventListener('change', updateButton);
  adultCheckbox.addEventListener('change', updateButton);

  continueButton.addEventListener('click', () => {
    if (!checkbox.checked || !adultCheckbox.checked) return;
    sessionStorage.setItem(TERMS_ACCEPTANCE_KEY, TERMS_VERSION);
    window.location.assign('/pages/cadastro.html');
  });
})();

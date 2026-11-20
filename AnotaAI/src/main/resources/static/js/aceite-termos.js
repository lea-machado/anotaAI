(async function () {
  const acceptButton = document.getElementById('review-accept');
  const feedback = document.getElementById('review-feedback');
  const termsCheck = document.getElementById('review-terms-check');
  let current;

  function renderDocument(legalDocument) {
    const node = document.getElementById('review-terms-content');
    if (legalDocument.formato === 'HTML') node.innerHTML = legalDocument.conteudo;
    else {
      const text = document.createElement('div');
      text.className = 'legal-text-content';
      text.textContent = legalDocument.conteudo;
      node.replaceChildren(text);
    }
  }

  function updateButton() {
    acceptButton.disabled = !termsCheck.checked || !current;
  }
  termsCheck.addEventListener('change', updateButton);

  document.getElementById('legal-logout').addEventListener('click', async () => {
    try { await Api.logout(); } finally { location.replace('/pages/login.html'); }
  });

  try {
    const [status, documentLegal] = await Promise.all([Api.getLegalStatus(), Api.getCurrentLegalDocuments()]);
    if (!status.precisaAceitar) {
      location.replace('/pages/anotacoes.html');
      return;
    }
    current = documentLegal;
    document.getElementById('review-terms-version').textContent = current.versao;
    renderDocument(current);
    updateButton();
  } catch (error) {
    feedback.textContent = error.message;
    feedback.hidden = false;
  }

  acceptButton.addEventListener('click', async () => {
    acceptButton.disabled = true;
    feedback.hidden = true;
    try {
      const result = await Api.acceptLegalTerms({
        aceitou: termsCheck.checked,
        versao: current.versao
      });
      if (result.precisaAceitar) throw new Error('Há uma nova versão. Atualize a página e aceite novamente.');
      const user = await Api.getCurrentUser();
      location.replace(user.perfil === 'ADMIN' ? '/pages/admin.html' : '/pages/anotacoes.html');
    } catch (error) {
      feedback.textContent = error.message;
      feedback.hidden = false;
      updateButton();
    }
  });
})();

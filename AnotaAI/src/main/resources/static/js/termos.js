(async function () {
  const acceptanceKey = 'anotaai.termosCadastroAceitos';
  const registration = new URLSearchParams(location.search).get('cadastro') === '1';
  const acceptance = document.getElementById('legal-acceptance');
  const documentNode = document.getElementById('legal-document');
  const versionLabel = document.getElementById('legal-version');
  const versionList = document.getElementById('legal-version-list');
  const checkbox = document.getElementById('accept-terms');
  const adultCheckbox = document.getElementById('confirm-adult');
  const continueButton = document.getElementById('continue-registration');
  let documento;
  let exibindoVersaoAtual = true;
  if (registration) acceptance.hidden = false;

  function renderDocument(doc, old = false) {
    if (doc.formato === 'HTML') documentNode.innerHTML = doc.conteudo;
    else {
      documentNode.replaceChildren();
      const paragraph = document.createElement('div');
      paragraph.className = 'legal-text-content';
      paragraph.textContent = doc.conteudo;
      documentNode.appendChild(paragraph);
    }
    versionLabel.textContent = `${old ? 'Versão anterior' : 'Versão atual'}: ${doc.versao} · Publicado em ${new Date(doc.publicadoEm).toLocaleDateString('pt-BR')}`;
    exibindoVersaoAtual = !old;
    if (registration) {
      checkbox.disabled = old;
      if (old) checkbox.checked = false;
      continueButton.disabled = old || !checkbox.checked || !adultCheckbox.checked;
    }
  }

  async function loadHistory() {
    versionList.textContent = 'Carregando histórico...';
    try {
      const response = await fetch('/api/termos/versoes', { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) throw new Error('Não foi possível carregar as versões.');
      const versions = await response.json();
      versionList.replaceChildren();
      versions.forEach(doc => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'legal-version-button';
        button.textContent = `${doc.versao} · ${new Date(doc.publicadoEm).toLocaleDateString('pt-BR')}${doc.id === documento.id ? ' · atual' : ''}`;
        button.addEventListener('click', () => renderDocument(doc, doc.id !== documento.id));
        versionList.append(button);
      });
    } catch (error) {
      versionList.textContent = error.message;
    }
  }

  try {
    const response = await fetch('/api/termos/atuais', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('Não foi possível carregar os termos atuais.');
    documento = await response.json();
    renderDocument(documento);
    await loadHistory();
    if (registration) {
      const update = () => {
        continueButton.disabled = !exibindoVersaoAtual || !checkbox.checked || !adultCheckbox.checked;
      };
      checkbox.addEventListener('change', update);
      adultCheckbox.addEventListener('change', update);
      continueButton.addEventListener('click', () => {
        if (continueButton.disabled || !exibindoVersaoAtual) return;
        sessionStorage.setItem(acceptanceKey, JSON.stringify({ versao: documento.versao }));
        location.assign('/pages/cadastro.html');
      });
    }
  } catch (error) {
    versionLabel.textContent = error.message;
    if (registration) continueButton.disabled = true;
  }
})();

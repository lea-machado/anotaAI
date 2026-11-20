(async function () {
  const form = document.getElementById('admin-legal-form');
  const current = document.getElementById('admin-legal-current');
  const history = document.getElementById('admin-legal-versions');
  const preview = document.getElementById('admin-legal-view');
  const previewTitle = document.getElementById('admin-legal-view-title');
  const feedback = document.getElementById('admin-legal-feedback');
  const publish = document.getElementById('admin-legal-publish');

  function showDocument(documento) {
    previewTitle.textContent = `${documento.versao} · Publicado em ${new Date(documento.publicadoEm).toLocaleString('pt-BR')}`;
    if (documento.formato === 'HTML') preview.innerHTML = documento.conteudo;
    else {
      const text = document.createElement('div');
      text.className = 'legal-text-content';
      text.textContent = documento.conteudo;
      preview.replaceChildren(text);
    }
  }

  async function loadHistory() {
    feedback.hidden = true;
    history.textContent = 'Carregando versões...';
    try {
      const [latest, versions] = await Promise.all([
        Api.getCurrentLegalDocuments(), Api.getLegalVersions()
      ]);
      current.textContent = `Versão vigente: ${latest.versao}`;
      const editor = document.getElementById('admin-legal-text');
      if (latest.formato === 'HTML') {
        const temporary = document.createElement('div');
        temporary.innerHTML = latest.conteudo;
        editor.value = temporary.innerText || temporary.textContent || '';
      } else editor.value = latest.conteudo;
      showDocument(latest);
      history.replaceChildren();
      versions.forEach(documento => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'legal-version-button';
        button.textContent = `${documento.versao} · ${new Date(documento.publicadoEm).toLocaleDateString('pt-BR')}${documento.id === latest.id ? ' · atual' : ''}`;
        button.addEventListener('click', () => showDocument(documento));
        history.append(button);
      });
    } catch (error) {
      history.textContent = error.message;
    }
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    feedback.hidden = true;
    publish.disabled = true;
    try {
      const content = document.getElementById('admin-legal-text').value.trim();
      if (content.length < 100) throw new Error('O documento deve ter no mínimo 100 caracteres.');
      await Api.publishLegalTerms({ conteudo: content });
      location.replace('/pages/aceite-termos.html');
    } catch (error) {
      feedback.textContent = error.message;
      feedback.className = 'admin-feedback error';
      feedback.hidden = false;
      publish.disabled = false;
    }
  });
  await loadHistory();
})();

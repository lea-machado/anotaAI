(async function () {
  const auditList = document.getElementById('audit-list');
  const form = document.getElementById('profile-form');
  const feedback = document.getElementById('profile-feedback');
  const saveButton = document.getElementById('save-profile');

  try {
    const [user, audit, performance] = await Promise.all([Api.getCurrentUser(), Api.getAudit(), Api.getPerformance()]);
    preencherPerfil(user);
    document.getElementById('profile-notes-count').textContent = String(performance.anotacoes).padStart(2, '0');
    if (user.perfil === 'ADMIN') addAdminNavigation();
    renderAudit(audit);
  } catch (error) {
    auditList.innerHTML = `<li><div><strong>${error.message}</strong></div></li>`;
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    feedback.textContent = '';
    feedback.className = 'profile-feedback full';
    saveButton.disabled = true;
    saveButton.textContent = 'Salvando...';

    try {
      const user = await Api.updateCurrentUser({
        nome: document.getElementById('nome').value,
        email: document.getElementById('email').value,
        escolaridade: document.getElementById('escolaridade').value
      });
      preencherPerfil(user);
      feedback.textContent = 'Perfil atualizado com sucesso.';
      feedback.classList.add('success');

      try {
        renderAudit(await Api.getAudit());
      } catch (_) {
        // A atualização do perfil já foi concluída; a auditoria é apenas complementar.
      }
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add('error');
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = 'Salvar alterações';
    }
  });

  function preencherPerfil(user) {
    document.getElementById('nome').value = user.nome || '';
    document.getElementById('email').value = user.email || '';
    document.getElementById('escolaridade').value = user.escolaridade || '';
    document.getElementById('profile-name').textContent = user.nome || 'Estudante AnotaAI';
    document.getElementById('profile-avatar').textContent = iniciais(user.nome);
    document.getElementById('member-since').textContent = `Conta ativa desde ${new Date(user.criadoEm).toLocaleDateString('pt-BR')}`;
  }

  function iniciais(nome) {
    const partes = (nome || 'EA').trim().split(/\s+/).filter(Boolean);
    return partes.slice(0, 2).map(parte => parte[0]).join('').toUpperCase() || 'EA';
  }

  function renderAudit(audit) {
    auditList.innerHTML = audit.length ? audit.map(item => `
      <li>
        <span class="audit-status ${item.sucesso ? 'success' : 'failure'}"></span>
        <div><strong>${formatAction(item.acao)}</strong><span>${new Date(item.criadoEm).toLocaleString('pt-BR')}</span></div>
      </li>`).join('') : '<li><div><strong>Nenhuma ação registrada</strong></div></li>';
  }

  function formatAction(action) {
    return action.toLowerCase().replaceAll('_', ' ').replace(/^./, value => value.toUpperCase());
  }

  function addAdminNavigation() {
    const navigation = document.querySelector('.sidebar-nav');
    if (navigation.querySelector('a[href="admin.html"]')) return;
    const link = document.createElement('a');
    link.className = 'nav-item';
    link.href = 'admin.html';
    link.innerHTML = `
      <svg aria-hidden="true" class="simple-svg" viewBox="0 0 24 24">
        <path d="M12 3 4 7v5c0 5 3.4 8 8 9 4.6-1 8-4 8-9V7l-8-4Z"></path>
        <path d="m9 12 2 2 4-4"></path>
      </svg>
      <span>Administração</span>`;
    navigation.appendChild(link);
  }
})();

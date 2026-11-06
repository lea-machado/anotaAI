(async function () {
  const usersBody = document.getElementById('users-body');
  const feedback = document.getElementById('admin-feedback');

  try {
    const [currentUser, summary, users, audit] = await Promise.all([
      Api.getCurrentUser(),
      Api.getAdminSummary(),
      Api.getAdminUsers(),
      Api.getAdminAudit()
    ]);

    document.getElementById('admin-users-count').textContent = summary.usuarios;
    document.getElementById('admin-notes-count').textContent = summary.anotacoes;
    document.getElementById('admin-audit-count').textContent = summary.registrosAuditoria;
    usersBody.innerHTML = users.map(user => userRow(user, currentUser.id)).join('');
    document.getElementById('audit-body').innerHTML = audit.map(item => `
      <tr>
        <td>${new Date(item.criadoEm).toLocaleString('pt-BR')}</td>
        <td>${escapeHtml(item.email)}</td>
        <td>${formatAction(item.acao)}</td>
        <td>${escapeHtml(item.recurso)}</td>
        <td>${escapeHtml(item.enderecoIp)}</td>
        <td><span class="status-badge ${item.sucesso ? 'success' : 'failure'}">${item.sucesso ? 'Sucesso' : 'Falha'}</span></td>
      </tr>`).join('');

    usersBody.addEventListener('change', alterarPerfil);
    usersBody.addEventListener('click', executarAcao);
  } catch (error) {
    document.getElementById('admin-content').innerHTML = `<p class="admin-error">${escapeHtml(error.message)}</p>`;
  }

  async function alterarPerfil(event) {
    const select = event.target.closest('[data-role-select]');
    if (!select) return;

    const perfilAnterior = select.dataset.currentRole;
    const novoPerfil = select.value;
    if (novoPerfil === perfilAnterior) return;

    select.disabled = true;
    feedback.hidden = true;
    try {
      const resultado = await Api.updateAdminUserRole(select.dataset.userId, novoPerfil);
      select.dataset.currentRole = resultado.perfil;
      select.value = resultado.perfil;
      showFeedback(resultado.mensagem, 'success');
    } catch (error) {
      select.value = perfilAnterior;
      showFeedback(error.message, 'error');
    } finally {
      select.disabled = false;
    }
  }

  async function executarAcao(event) {
    const button = event.target.closest('[data-user-action]');
    if (!button || button.disabled) return;
    const id = button.dataset.userId;
    if (button.dataset.userAction === 'delete' && !window.confirm('Excluir este usuário e todos os dados vinculados? Essa ação não pode ser desfeita.')) return;
    button.disabled = true;
    try {
      const result = button.dataset.userAction === 'delete'
        ? await Api.deleteAdminUser(id)
        : await Api.toggleAdminUserStatus(id);
      showFeedback(result.mensagem, 'success');
      const [users, currentUser] = await Promise.all([Api.getAdminUsers(), Api.getCurrentUser()]);
      usersBody.innerHTML = users.map(user => userRow(user, currentUser.id)).join('');
    } catch (error) {
      button.disabled = false;
      showFeedback(error.message, 'error');
    }
  }

  function userRow(user, currentUserId) {
    const isCurrentUser = user.id === currentUserId;
    const ativo = user.ativo !== false;
    return `
      <tr>
        <td>${escapeHtml(user.nome)}${isCurrentUser ? '<span class="current-user-label">Você</span>' : ''}</td>
        <td>${escapeHtml(user.email)}</td>
        <td>
          <select class="role-select" data-current-role="${user.perfil}" data-role-select data-user-id="${user.id}"
                  aria-label="Perfil de ${escapeHtml(user.nome)}" ${isCurrentUser ? 'disabled title="Seu próprio perfil não pode ser alterado"' : ''}>
            <option value="USER" ${user.perfil === 'USER' ? 'selected' : ''}>Usuário</option>
            <option value="ADMIN" ${user.perfil === 'ADMIN' ? 'selected' : ''}>Administrador</option>
          </select>
        </td>
        <td><span class="status-badge ${ativo ? 'success' : 'failure'}">${ativo ? 'Ativo' : 'Desativado'}</span></td>
        <td>${user.anotacoes}</td>
        <td>${new Date(user.criadoEm).toLocaleDateString('pt-BR')}</td>
        <td class="admin-actions">
          <button class="admin-action" data-user-action="status" data-user-id="${user.id}" ${isCurrentUser ? 'disabled' : ''}>${ativo ? 'Desativar' : 'Ativar'}</button>
          <button class="admin-action danger" data-user-action="delete" data-user-id="${user.id}" ${isCurrentUser ? 'disabled' : ''}>Excluir</button>
        </td>
      </tr>`;
  }

  function showFeedback(message, type) {
    feedback.textContent = message;
    feedback.className = `admin-feedback ${type}`;
    feedback.hidden = false;
  }

  function formatAction(value) {
    return value.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase());
  }

  function escapeHtml(value) {
    const element = document.createElement('span');
    element.textContent = String(value);
    return element.innerHTML;
  }
})();

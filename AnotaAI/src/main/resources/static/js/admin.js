(async function () {
  const usersBody = document.getElementById('users-body');
  const feedback = document.getElementById('admin-feedback');
  const deletionFeedback = document.getElementById('deletion-feedback');
  const deletionRequestsBody = document.getElementById('deletion-requests-body');
  const confirmationModal = document.getElementById('admin-confirm-modal');
  const confirmationTitle = document.getElementById('admin-confirm-title');
  const confirmationMessage = document.getElementById('admin-confirm-message');
  const confirmationCancel = document.getElementById('admin-confirm-cancel');
  const confirmationAccept = document.getElementById('admin-confirm-accept');
  let finishConfirmation = null;
  let currentUser;
  let allUsers = [];
  const summaryCache = new Map();
  const auditCache = new Map();
  let filterRequest = 0;

  try {
    const [loggedInUser, users, deletionRequests] = await Promise.all([
      Api.getCurrentUser(),
      Api.getAdminUsers(),
      Api.getAdminDeletionRequests()
    ]);
    currentUser = loggedInUser;
    allUsers = users;
    renderDeletionRequests(deletionRequests);
    usersBody.addEventListener('change', alterarPerfil);
    usersBody.addEventListener('click', executarAcao);
    deletionRequestsBody.addEventListener('click', concluirPedidoExclusao);
    confirmationCancel.addEventListener('click', () => closeConfirmation(false));
    confirmationAccept.addEventListener('click', () => closeConfirmation(true));
    confirmationModal.addEventListener('click', event => {
      if (event.target === confirmationModal) closeConfirmation(false);
    });
    document.addEventListener('keydown', event => {
      if (confirmationModal.hidden) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        closeConfirmation(false);
      } else if (event.key === 'Tab') {
        event.preventDefault();
        (document.activeElement === confirmationCancel ? confirmationAccept : confirmationCancel).focus();
      }
    });
    ['dashboard-period', 'dashboard-start', 'dashboard-end', 'audit-search', 'audit-status-filter'].forEach(id => {
      document.getElementById(id).addEventListener('input', applyFilters);
      document.getElementById(id).addEventListener('change', applyFilters);
    });
    document.getElementById('users-search').addEventListener('input', renderUsers);
    document.getElementById('users-role-filter').addEventListener('change', renderUsers);
    document.getElementById('users-status-filter').addEventListener('change', renderUsers);
    document.getElementById('dashboard-refresh').addEventListener('click', refreshDashboard);
    renderUsers();
    applyFilters();
  } catch (error) {
    document.getElementById('admin-content').innerHTML = `<p class="admin-error">${escapeHtml(error.message)}</p>`;
  }

  function showConfirmation(title, message, acceptLabel) {
    confirmationTitle.textContent = title;
    confirmationMessage.textContent = message;
    confirmationAccept.textContent = acceptLabel;
    App.openModal('admin-confirm-modal');
    requestAnimationFrame(() => confirmationCancel.focus());
    return new Promise(resolve => { finishConfirmation = resolve; });
  }

  function closeConfirmation(confirmed) {
    if (!finishConfirmation) return;
    const resolve = finishConfirmation;
    finishConfirmation = null;
    App.closeModal(confirmationModal);
    resolve(confirmed);
  }      

  async function refreshDashboard() {
    const button = document.getElementById('dashboard-refresh');
    button.disabled = true;
    try {
      const [users, requests] = await Promise.all([Api.getAdminUsers(), Api.getAdminDeletionRequests()]);
      allUsers = users;
      renderUsers();
      renderDeletionRequests(requests);
      summaryCache.clear();
      auditCache.clear();
      await applyFilters();
    } catch (error) {
      showFeedback(error.message, 'error');
    } finally {
      button.disabled = false;
    }
  }

  function renderDeletionRequests(requests) {
    document.getElementById('deletion-request-count').textContent = `${requests.length} solicitação(ões) pendente(s)`;
    document.getElementById('empty-deletion-requests').hidden = requests.length > 0;
    deletionRequestsBody.innerHTML = requests.map(request => `
      <tr>
        <td>${request.solicitadaEm ? new Date(request.solicitadaEm).toLocaleString('pt-BR') : '—'}<br><small>${request.solicitadaEm ? deletionDeadline(request.solicitadaEm) : 'Data não informada'}</small></td>
        <td>${escapeHtml(request.nome)}</td>
        <td>${escapeHtml(request.email)}</td>
        <td><button class="admin-action danger" data-deletion-request="${request.id}" data-user-id="${request.usuarioId}">Excluir conta</button></td>
      </tr>`).join('');
  }

  function deletionDeadline(requestedAt) {
    const requested = new Date(requestedAt);
    const deadline = new Date(requested);
    deadline.setDate(deadline.getDate() + 15);
    const daysLeft = Math.ceil((deadline - new Date()) / 86400000);
    return daysLeft < 0
      ? `Prazo de 15 dias vencido em ${deadline.toLocaleDateString('pt-BR')}`
      : `Prazo: ${deadline.toLocaleDateString('pt-BR')} (${daysLeft} dia(s))`;
  }

  function confirmAccountDeletion() {
    return showConfirmation(
      'Excluir esta conta?',
      'A conta, as anotações, o histórico de versões e os flashcards serão excluídos permanentemente.',
      'Excluir conta'
    );
  }

  async function concluirPedidoExclusao(event) {
    const button = event.target.closest('[data-deletion-request]');
    if (!button || button.disabled || !await confirmAccountDeletion()) return;
    button.disabled = true;
    try {
      const result = await Api.completeAdminDeletionRequest(button.dataset.deletionRequest);
      deletionFeedback.textContent = result.mensagem;
      deletionFeedback.className = result.emailEnviado === false ? 'admin-feedback error' : 'admin-feedback success';
      deletionFeedback.hidden = false;
      await refreshDashboard();
    } catch (error) {
      deletionFeedback.textContent = error.message;
      deletionFeedback.className = 'admin-feedback error';
      deletionFeedback.hidden = false;
      button.disabled = false;
    }
  }

  async function applyFilters() {
    const request = ++filterRequest;
    const period = document.getElementById('dashboard-period').value;
    const custom = period === 'custom';
    document.querySelectorAll('.custom-date-filter').forEach(label => { label.hidden = !custom; });
    if (custom && !document.getElementById('dashboard-start').value && !document.getElementById('dashboard-end').value) {
      const today = new Date();
      const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate() - 6);
      document.getElementById('dashboard-start').value = dateInputValue(weekAgo);
      document.getElementById('dashboard-end').value = dateInputValue(today);
    }
    let start;
    let end;
    if (period === 'all') {
      start = null;
      end = null;
    } else if (custom) {
      const startValue = document.getElementById('dashboard-start').value;
      const endValue = document.getElementById('dashboard-end').value;
      if (!startValue || !endValue || startValue > endValue) {
        document.getElementById('dashboard-period-label').textContent = 'Escolha um intervalo válido.';
        return;
      }
      start = new Date(`${startValue}T00:00:00`);
      end = new Date(`${endValue}T23:59:59.999`);
    } else {
      end = new Date();
      start = new Date();
      start.setHours(0, 0, 0, 0);
      start.setDate(start.getDate() - Number(period) + 1);
    }

    const params = new URLSearchParams();
    if (start && end) {
      params.set('inicio', dateInputValue(start));
      params.set('fim', dateInputValue(end));
    }
    try {
      const query = params.toString();
      const cacheKey = query || 'all';
      if (!summaryCache.has(cacheKey)) summaryCache.set(cacheKey, await Api.getAdminSummary(query));
      if (request !== filterRequest) return;
      const summary = summaryCache.get(cacheKey);
      renderDashboard(summary);
                                                                                   
      const matchesPeriod = date => {
        if (!start && !end) return true;
        if (!date) return false;
        const value = new Date(date);
        return value >= start && value <= end;
      };
      renderSignupChart(allUsers.filter(user => matchesPeriod(user.criadoEm)), start, end, period);
      document.getElementById('dashboard-period-label').textContent = periodLabel(start, end, period);
      await renderAudit(params, request);
    } catch (error) {
      if (request === filterRequest) showFeedback(error.message, 'error');
    }
  }

  function renderUsers() {
    const search = document.getElementById('users-search').value.trim().toLocaleLowerCase('pt-BR');
    const role = document.getElementById('users-role-filter').value;
    const status = document.getElementById('users-status-filter').value;
    const users = allUsers.filter(user => {
      const text = `${user.nome} ${user.email}`.toLocaleLowerCase('pt-BR');
      return text.includes(search)
        && (role === 'all' || user.perfil === role)
        && (status === 'all' || (status === 'active' ? user.ativo !== false : user.ativo === false));
    });
    usersBody.innerHTML = users.map(user => userRow(user, currentUser.id)).join('');
    document.getElementById('users-result-count').textContent = `${users.length} de ${allUsers.length} usuários`;
  }

  async function renderAudit(params, request) {
    const status = document.getElementById('audit-status-filter').value;
    if (status !== 'all') params.set('sucesso', String(status === 'success'));
    const query = params.toString();
    if (!auditCache.has(query)) {
      const audit = await Api.getAdminAudit(query);
      if (request !== filterRequest) return;
      auditCache.set(query, audit);
    }
    const search = document.getElementById('audit-search').value.trim().toLocaleLowerCase('pt-BR');
    const items = auditCache.get(query).filter(item => {
      const text = `${item.email} ${item.acao ?? ''} ${item.recurso ?? ''} ${item.enderecoIp}`.toLocaleLowerCase('pt-BR');
      return text.includes(search);
    });
    document.getElementById('audit-body').innerHTML = items.length ? items.map(auditRow).join('')
      : '<tr><td colspan="6">Nenhum evento encontrado para os filtros selecionados.</td></tr>';
    document.getElementById('audit-result-count').textContent = `${items.length} eventos exibidos (até 100 recentes)`;
  }

  function renderDashboard(summary) {
    document.getElementById('admin-users-count').textContent = summary.usuarios;
    document.getElementById('admin-notes-count').textContent = summary.anotacoes;
    document.getElementById('admin-active-count').textContent = summary.usuariosAtivos;
    document.getElementById('admin-inactive-count').textContent = summary.usuariosDesativados;
    document.getElementById('admin-admins-count').textContent = summary.administradores;
    document.getElementById('admin-new-users').textContent = summary.novosUltimos7Dias;
    document.getElementById('admin-decks-count').textContent = summary.baralhos;
    document.getElementById('admin-cards-count').textContent = summary.flashcards;
    document.getElementById('admin-failures-count').textContent = summary.falhasAuditoria;
    document.getElementById('admin-ai-usage-count').textContent = summary.usosIa;
    document.getElementById('health-audit').textContent = summary.registrosAuditoria;
    document.getElementById('health-failures').textContent = summary.falhasAuditoria;
    const activePercent = summary.usuarios ? Math.round(summary.usuariosAtivos * 100 / summary.usuarios) : 0;
    document.getElementById('health-active').textContent = `${activePercent}%`;
    document.getElementById('health-active-bar').style.width = `${activePercent}%`;
  }

  function auditRow(item) {
    return `<tr><td>${item.criadoEm ? new Date(item.criadoEm).toLocaleString('pt-BR') : '—'}</td><td>${escapeHtml(item.email)}</td><td>${escapeHtml(formatAction(item.acao ?? '') || '—')}</td><td>${escapeHtml(item.recurso ?? '—')}</td><td>${escapeHtml(item.enderecoIp)}</td><td><span class="status-badge ${item.sucesso ? 'success' : 'failure'}">${item.sucesso ? 'Sucesso' : 'Falha'}</span></td></tr>`;
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
      await refreshDashboard();
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
    if (button.dataset.userAction === 'delete' && !await confirmAccountDeletion()) return;
    button.disabled = true;
    try {
      const result = button.dataset.userAction === 'delete'
        ? await Api.deleteAdminUser(id)
        : await Api.toggleAdminUserStatus(id);
      showFeedback(result.mensagem, result.emailEnviado === false ? 'error' : 'success');
      await refreshDashboard();
    } catch (error) {
      button.disabled = false;
      showFeedback(error.message, 'error');
    }
  }

  function userRow(user, currentUserId) {
    const isCurrentUser = user.id === currentUserId;
    const anonymized = user.anonimizado === true;
    const ativo = user.ativo !== false;
    return `
      <tr>
        <td>${escapeHtml(user.nome)}${isCurrentUser ? '<span class="current-user-label">Você</span>' : ''}${anonymized ? '<span class="current-user-label">Anonimizado</span>' : ''}</td>
        <td>${escapeHtml(user.email)}</td>
        <td>
          <select class="role-select" data-current-role="${user.perfil}" data-role-select data-user-id="${user.id}"
                  aria-label="Perfil de ${escapeHtml(user.nome)}" ${isCurrentUser || anonymized ? 'disabled title="Conta anonimizada"' : ''}>
            <option value="USER" ${user.perfil === 'USER' ? 'selected' : ''}>Usuário</option>
            <option value="ADMIN" ${user.perfil === 'ADMIN' ? 'selected' : ''}>Administrador</option>
          </select>
        </td>
        <td><span class="status-badge ${user.pedidoExclusao || anonymized ? 'failure' : (ativo ? 'success' : 'failure')}">${anonymized ? 'Anonimizado' : (user.pedidoExclusao ? 'Pedido pendente' : (ativo ? 'Ativo' : 'Desativado'))}</span></td>
        <td>${user.anotacoes}</td>
        <td>${user.criadoEm ? new Date(user.criadoEm).toLocaleDateString('pt-BR') : '—'}</td>
        <td class="admin-actions">
          <button class="admin-action" data-user-action="status" data-user-id="${user.id}" ${isCurrentUser || user.pedidoExclusao || anonymized ? 'disabled' : ''}>${anonymized ? 'Conta encerrada' : (user.pedidoExclusao ? 'Aguardando exclusão' : (ativo ? 'Desativar' : 'Ativar'))}</button>
          <button class="admin-action danger" data-user-action="delete" data-user-id="${user.id}" ${isCurrentUser || !user.pedidoExclusao ? 'disabled' : ''}>${user.pedidoExclusao ? 'Excluir' : 'Sem pedido'}</button>
        </td>
      </tr>`;
  }

  function showFeedback(message, type) {
    feedback.textContent = message;
    feedback.className = `admin-feedback ${type}`;
    feedback.hidden = false;
  }

  function renderSignupChart(users, start, end, period) {
    const chart = document.getElementById('signup-chart');
    let chartStart = start ? new Date(start) : new Date();
    let chartEnd = end ? new Date(end) : new Date();
    if (period === 'all') {
      chartStart = new Date(); chartStart.setHours(0, 0, 0, 0); chartStart.setDate(chartStart.getDate() - 6);
      chartEnd = new Date();
    }
    const span = Math.max(1, Math.floor((chartEnd - chartStart) / 86400000) + 1);
    const bucketSize = Math.max(1, Math.ceil(span / 14));
    const buckets = Array.from({ length: Math.ceil(span / bucketSize) }, (_, index) => {
      const from = new Date(chartStart); from.setDate(from.getDate() + index * bucketSize);
      const to = new Date(from); to.setDate(to.getDate() + bucketSize);
      const total = users.filter(user => {
        if (!user.criadoEm) return false;
        const created = new Date(user.criadoEm);
        return created >= from && created < to;
      }).length;
      return { date: from, total };
    });
    const peak = Math.max(1, ...buckets.map(bucket => bucket.total));
    chart.innerHTML = buckets.map(bucket => `<div class="signup-day"><span class="signup-count">${bucket.total}</span><div class="signup-bar-wrap"><span class="signup-bar" style="height:${Math.max(5, bucket.total / peak * 100)}%"></span></div><span class="signup-label">${bucket.date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}</span></div>`).join('');
  }

  function dateInputValue(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  function periodLabel(start, end, period) {
    if (period === 'all') return 'Todo o período';
    return `${start.toLocaleDateString('pt-BR')} a ${end.toLocaleDateString('pt-BR')}`;
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

(async function () {
  const usersBody = document.getElementById('users-body');
  const feedback = document.getElementById('admin-feedback');
  const deletionFeedback = document.getElementById('deletion-feedback');
  const deletionRequestsBody = document.getElementById('deletion-requests-body');
  let currentUser;
  let allUsers = [];
  let allAudit = [];
  const summaryCache = new Map();

  try {
    const [loggedInUser, users, audit, deletionRequests] = await Promise.all([
      Api.getCurrentUser(),
      Api.getAdminUsers(),
      Api.getAdminAudit(),
      Api.getAdminDeletionRequests()
    ]);
    currentUser = loggedInUser;
    allUsers = users;
    allAudit = audit;
    renderDeletionRequests(deletionRequests);
    usersBody.addEventListener('change', alterarPerfil);
    usersBody.addEventListener('click', executarAcao);
    deletionRequestsBody.addEventListener('click', concluirPedidoExclusao);
    ['dashboard-period', 'dashboard-start', 'dashboard-end', 'users-search', 'users-role-filter', 'users-status-filter', 'audit-search', 'audit-status-filter'].forEach(id => {
      document.getElementById(id).addEventListener('input', applyFilters);
      document.getElementById(id).addEventListener('change', applyFilters);
    });
    document.getElementById('dashboard-refresh').addEventListener('click', refreshDashboard);
    applyFilters();
  } catch (error) {
    document.getElementById('admin-content').innerHTML = `<p class="admin-error">${escapeHtml(error.message)}</p>`;
  }

  async function refreshDashboard() {
    const button = document.getElementById('dashboard-refresh');
    button.disabled = true;
    try {
      const [users, audit, requests] = await Promise.all([Api.getAdminUsers(), Api.getAdminAudit(), Api.getAdminDeletionRequests()]);
      allUsers = users;
      allAudit = audit;
      renderDeletionRequests(requests);
      summaryCache.clear();
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
        <td>${new Date(request.solicitadaEm).toLocaleString('pt-BR')}<br><small>${deletionDeadline(request.solicitadaEm)}</small></td>
        <td>${escapeHtml(request.nome)}</td>
        <td>${escapeHtml(request.email)}</td>
        <td><button class="admin-action danger" data-deletion-request="${request.id}" data-user-id="${request.usuarioId}">Excluir conta e dados</button></td>
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

  async function concluirPedidoExclusao(event) {
    const button = event.target.closest('[data-deletion-request]');
    if (!button || button.disabled) return;
    if (!window.confirm('Excluir permanentemente a conta e todos os dados vinculados a este pedido? Esta ação não pode ser desfeita.')) return;
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
      const summary = summaryCache.get(cacheKey);
      renderDashboard(summary);
      const matchesPeriod = date => {
        if (!start && !end) return true;
        if (!date) return false;
        const value = new Date(date);
        return value >= start && value <= end;
      };
      const usersPeriod = allUsers.filter(user => matchesPeriod(user.criadoEm));
      const auditPeriod = allAudit.filter(item => matchesPeriod(item.criadoEm));
      const userSearch = document.getElementById('users-search').value.trim().toLocaleLowerCase('pt-BR');
      const role = document.getElementById('users-role-filter').value;
      const status = document.getElementById('users-status-filter').value;
      const filteredUsers = usersPeriod.filter(user => {
        const text = `${user.nome} ${user.email}`.toLocaleLowerCase('pt-BR');
        return (!userSearch || text.includes(userSearch))
          && (role === 'all' || user.perfil === role)
          && (status === 'all' || (status === 'active' ? user.ativo !== false : user.ativo === false));
      });
      usersBody.innerHTML = filteredUsers.map(user => userRow(user, currentUser.id)).join('');
      document.getElementById('users-result-count').textContent = `${filteredUsers.length} de ${usersPeriod.length} usuários no período`;

      const auditSearch = document.getElementById('audit-search').value.trim().toLocaleLowerCase('pt-BR');
      const auditStatus = document.getElementById('audit-status-filter').value;
      const filteredAudit = auditPeriod.filter(item => {
        const text = `${item.email} ${item.acao} ${item.recurso} ${item.enderecoIp}`.toLocaleLowerCase('pt-BR');
        return (!auditSearch || text.includes(auditSearch))
          && (auditStatus === 'all' || (auditStatus === 'success' ? item.sucesso : !item.sucesso));
      });
      document.getElementById('audit-body').innerHTML = filteredAudit.map(auditRow).join('');
      document.getElementById('audit-result-count').textContent = `${filteredAudit.length} eventos exibidos (até 100 recentes)`;
      renderSignupChart(usersPeriod, start, end, period);
      document.getElementById('dashboard-period-label').textContent = periodLabel(start, end, period);
    } catch (error) {
      showFeedback(error.message, 'error');
    }
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
    return `<tr><td>${new Date(item.criadoEm).toLocaleString('pt-BR')}</td><td>${escapeHtml(item.email)}</td><td>${formatAction(item.acao)}</td><td>${escapeHtml(item.recurso)}</td><td>${escapeHtml(item.enderecoIp)}</td><td><span class="status-badge ${item.sucesso ? 'success' : 'failure'}">${item.sucesso ? 'Sucesso' : 'Falha'}</span></td></tr>`;
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
    if (button.dataset.userAction === 'delete' && !window.confirm('Excluir este usuário e todos os dados vinculados? Essa ação não pode ser desfeita.')) return;
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
        <td><span class="status-badge ${user.pedidoExclusao ? 'failure' : (ativo ? 'success' : 'failure')}">${user.pedidoExclusao ? 'Exclusão pendente' : (ativo ? 'Ativo' : 'Desativado')}</span></td>
        <td>${user.anotacoes}</td>
        <td>${new Date(user.criadoEm).toLocaleDateString('pt-BR')}</td>
        <td class="admin-actions">
          <button class="admin-action" data-user-action="status" data-user-id="${user.id}" ${isCurrentUser || user.pedidoExclusao ? 'disabled' : ''}>${user.pedidoExclusao ? 'Aguardando exclusão' : (ativo ? 'Desativar' : 'Ativar')}</button>
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

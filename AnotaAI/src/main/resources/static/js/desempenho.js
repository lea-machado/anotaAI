(async function () {
  function formatNumber(value) {
    return Number(value || 0).toLocaleString('pt-BR');
  }

  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  try {
    const desempenho = await Api.getPerformance();
    document.getElementById('metric-notes').textContent = formatNumber(desempenho.anotacoes);
    document.getElementById('metric-chars').textContent = formatNumber(desempenho.caracteres);
    document.getElementById('metric-average').textContent = formatNumber(desempenho.mediaCaracteres);
    document.getElementById('metric-last').textContent = formatDate(desempenho.ultimaAtualizacao);
    document.getElementById('metric-flashcards').textContent = formatNumber(desempenho.flashcards);
    document.getElementById('metric-correct').textContent = formatNumber(desempenho.acertos);
    document.getElementById('metric-wrong').textContent = formatNumber(desempenho.erros);
    document.getElementById('metric-correct-rate').textContent = `${desempenho.taxaAcertos}% das respostas registradas`;
    document.getElementById('metric-wrong-rate').textContent = `${desempenho.taxaErros}% das respostas registradas`;
  } catch (error) {
    App.toast(error.message, 'error');
  }
})();

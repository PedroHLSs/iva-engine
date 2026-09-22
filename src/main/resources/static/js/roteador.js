/* ---------------------------------------------------------------------------
   Roteamento por fragmento (#/...).

   Por fragmento, e nao por caminho, para que o servidor nao precise de nenhuma
   regra de reescrita: o navegador nunca pede "/execucao/<id>", so pede "/" e
   troca o que vem depois do "#". Nenhum arquivo Java precisou existir para esta
   tela funcionar, que e a condicao da etapa.
   --------------------------------------------------------------------------- */

/** Interpreta o fragmento atual. */
export function rotaAtual() {
  const bruto = window.location.hash.replace(/^#/, '');
  const [caminho, consulta] = bruto.split('?');
  const partes = caminho.split('/').filter((parte) => parte !== '');
  const parametros = new URLSearchParams(consulta || '');

  if (partes.length === 0) {
    return { nome: 'execucoes', parametros };
  }
  if (partes[0] === 'acuracia') {
    return { nome: 'acuracia', parametros };
  }
  if (partes[0] === 'execucao' && partes[1]) {
    const execucaoId = decodeURIComponent(partes[1]);
    if (partes[2] === 'achados') {
      return { nome: 'achados', execucaoId, parametros };
    }
    if (partes[2] === 'achado' && partes[3]) {
      return {
        nome: 'achado', execucaoId, achadoId: decodeURIComponent(partes[3]), parametros,
      };
    }
    if (partes[2] === 'nao-avaliados') {
      return { nome: 'naoAvaliados', execucaoId, parametros };
    }
    return { nome: 'panorama', execucaoId, parametros };
  }
  return { nome: 'desconhecida', caminho, parametros };
}

/** Monta um endereco de tela, para usar em href. */
export function endereco(nome, execucaoId, parametros = {}) {
  const consulta = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros)) {
    if (valor !== null && valor !== undefined && valor !== '') {
      consulta.set(chave, valor);
    }
  }
  const cauda = consulta.toString() ? '?' + consulta.toString() : '';
  const id = encodeURIComponent(execucaoId || '');

  switch (nome) {
    case 'execucoes': return '#/' + cauda;
    case 'acuracia': return '#/acuracia' + cauda;
    case 'panorama': return '#/execucao/' + id + cauda;
    case 'achados': return '#/execucao/' + id + '/achados' + cauda;
    case 'naoAvaliados': return '#/execucao/' + id + '/nao-avaliados' + cauda;
    default: return '#/';
  }
}

/** Endereco do detalhe de um achado. */
export function enderecoDoAchado(execucaoId, achadoId) {
  return '#/execucao/' + encodeURIComponent(execucaoId) + '/achado/'
    + encodeURIComponent(achadoId);
}

/** Troca o fragmento sem recarregar a pagina. */
export function irPara(destino) {
  window.location.hash = destino.replace(/^#/, '');
}

/** Chama o ouvinte agora e a cada troca de fragmento. */
export function aoTrocarDeRota(ouvinte) {
  window.addEventListener('hashchange', ouvinte);
  ouvinte();
}

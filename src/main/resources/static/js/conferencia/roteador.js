/* ---------------------------------------------------------------------------
   Roteamento por fragmento da interface de conferencia.

   Proprio, e nao o da Etapa 9, porque as rotas sao outras e as duas interfaces
   sao paginas separadas: esta em index.html, aquela em tecnica.html. Compartilhar
   um roteador so faria uma tabela de rotas em que metade nunca resolve.

   Por fragmento pelo mesmo motivo de la: o servidor nao precisa de regra de
   reescrita, porque o navegador so pede "/" e troca o que vem depois do "#".
   --------------------------------------------------------------------------- */

/** Interpreta o fragmento atual. */
export function rotaAtual() {
  const bruto = window.location.hash.replace(/^#/, '');
  const [caminho, consulta] = bruto.split('?');
  const partes = caminho.split('/').filter((parte) => parte !== '');
  const parametros = new URLSearchParams(consulta || '');

  if (partes.length === 0) {
    return { nome: 'enviar', parametros };
  }
  if (partes[0] === 'base') {
    return { nome: 'base', parametros };
  }
  if (partes[0] === 'historico') {
    return { nome: 'historico', parametros };
  }
  if (partes[0] === 'analise' && partes[1]) {
    const analiseId = decodeURIComponent(partes[1]);
    if (partes[2] === 'produto' && partes[3]) {
      return {
        nome: 'produto', analiseId, endereco: decodeURIComponent(partes[3]), parametros,
      };
    }
    if (partes[2] === 'grupo') {
      return { nome: 'grupo', analiseId, parametros };
    }
    return { nome: 'resultado', analiseId, parametros };
  }
  return { nome: 'desconhecida', caminho, parametros };
}

function cauda(parametros) {
  const consulta = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros || {})) {
    if (valor !== null && valor !== undefined && valor !== '') {
      consulta.set(chave, valor);
    }
  }
  return consulta.toString() ? '?' + consulta.toString() : '';
}

/** Endereco da tela de envio. */
export function enderecoDeEnvio() {
  return '#/';
}

/** Endereco do resultado de uma analise. */
export function enderecoDoResultado(analiseId, parametros) {
  return '#/analise/' + encodeURIComponent(analiseId) + cauda(parametros);
}

/** Endereco do detalhe de um produto. */
export function enderecoDoProduto(analiseId, endereco) {
  return '#/analise/' + encodeURIComponent(analiseId)
    + '/produto/' + encodeURIComponent(endereco);
}

/**
 * Endereco dos produtos de um grupo.
 *
 * Os tres componentes da chave vao na consulta, e nao num codigo sintetico: a
 * URL diz o que esta sendo olhado, e componente ausente se escreve omitindo o
 * parametro — que e a mesma ausencia que o agrupamento registrou.
 */
export function enderecoDoGrupo(analiseId, grupo) {
  return '#/analise/' + encodeURIComponent(analiseId) + '/grupo' + cauda({
    ncm: grupo.ncm,
    cClassTrib: grupo.cClassTrib,
    situacao: grupo.situacao,
  });
}

/** Endereco da base tributaria numa data. */
export function enderecoDaBase(parametros) {
  return '#/base' + cauda(parametros);
}

/** Endereco do historico. */
export function enderecoDoHistorico() {
  return '#/historico';
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

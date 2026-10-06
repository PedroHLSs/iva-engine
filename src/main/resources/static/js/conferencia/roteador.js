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
  if (partes[0] === 'entrar') {
    return { nome: 'entrar', parametros };
  }
  if (partes[0] === 'senha') {
    return { nome: 'senha', parametros };
  }
  if (partes[0] === 'usuarios') {
    return { nome: 'usuarios', parametros };
  }
  if (partes[0] === 'acuracia') {
    return { nome: 'acuracia', parametros };
  }
  if (partes[0] === 'cargas') {
    return { nome: 'cargas', parametros };
  }
  if (partes[0] === 'carga' && partes[1]) {
    return { nome: 'carga', versao: decodeURIComponent(partes[1]), parametros };
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

export function enderecoDeEnvio() {
  return '#/';
}

export function enderecoDoResultado(analiseId, parametros) {
  return '#/analise/' + encodeURIComponent(analiseId) + cauda(parametros);
}

export function enderecoDoProduto(analiseId, endereco) {
  return '#/analise/' + encodeURIComponent(analiseId)
    + '/produto/' + encodeURIComponent(endereco);
}

export function enderecoDoGrupo(analiseId, grupo) {
  return '#/analise/' + encodeURIComponent(analiseId) + '/grupo' + cauda({
    ncm: grupo.ncm,
    cClassTrib: grupo.cClassTrib,
    situacao: grupo.situacao,
  });
}

export function enderecoDaBase(parametros) {
  return '#/base' + cauda(parametros);
}

export function enderecoDoHistorico() {
  return '#/historico';
}

export function enderecoDasCargas() {
  return '#/cargas';
}

export function enderecoDaCarga(versao) {
  return '#/carga/' + encodeURIComponent(versao);
}

export function irPara(destino) {
  window.location.hash = destino.replace(/^#/, '');
}

export function aoTrocarDeRota(ouvinte) {
  window.addEventListener('hashchange', ouvinte);
  ouvinte();
}

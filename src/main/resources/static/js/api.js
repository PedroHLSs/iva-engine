export const TAMANHO_MAXIMO = 500;

export class FalhaDeLeitura extends Error {
  constructor(mensagem, detalhe) {
    super(mensagem);
    this.name = 'FalhaDeLeitura';
    this.detalhe = detalhe || null;
  }
}

const INVOCACAO =
  'java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar servir';

async function pegar(caminho) {
  let resposta;
  try {
    resposta = await fetch(caminho, { headers: { Accept: 'application/json' } });
  } catch (falhaDeRede) {
    throw new FalhaDeLeitura(
      'Nao foi possivel falar com a API de leitura. Ela sobe com o perfil "api" ativo, '
        + 'e escuta so em 127.0.0.1.',
      INVOCACAO,
    );
  }
  const corpo = await resposta.text();
  let json = null;
  try {
    json = corpo ? JSON.parse(corpo) : null;
  } catch (naoEraJson) {
    json = null;
  }
  if (!resposta.ok) {
    const mensagem = json && json.mensagem
      ? json.mensagem
      : `A API respondeu ${resposta.status} para ${caminho}.`;
    throw new FalhaDeLeitura(mensagem, json && json.erro ? json.erro : null);
  }
  if (json === null) {
    throw new FalhaDeLeitura(`A API respondeu sem corpo para ${caminho}.`);
  }
  return json;
}

function consulta(parametros) {
  const partes = [];
  for (const [nome, valor] of Object.entries(parametros)) {
    if (valor !== null && valor !== undefined && valor !== '') {
      partes.push(`${encodeURIComponent(nome)}=${encodeURIComponent(valor)}`);
    }
  }
  return partes.length ? `?${partes.join('&')}` : '';
}

export function execucoes(limite) {
  return pegar(`/api/execucoes${consulta({ limite })}`);
}

export function execucao(id) {
  return pegar(`/api/execucoes/${encodeURIComponent(id)}`);
}

export function paginaDeAchados(id, filtros, pagina, tamanho) {
  const busca = consulta({
    regra: filtros.regra,
    severidade: filtros.severidade,
    status: filtros.status,
    pagina,
    tamanho,
  });
  return pegar(`/api/execucoes/${encodeURIComponent(id)}/achados${busca}`);
}

export function paginaDeNaoAvaliados(id, filtros, pagina, tamanho) {
  const busca = consulta({ regra: filtros.regra, pagina, tamanho });
  return pegar(`/api/execucoes/${encodeURIComponent(id)}/nao-avaliados${busca}`);
}

export async function todasAsPaginas(buscarPagina, aoProgredir) {
  const acumulado = [];
  let numero = 0;
  let totalDePaginas = 1;
  let ultima = null;

  do {
    ultima = await buscarPagina(numero, TAMANHO_MAXIMO);
    const linhas = ultima.achados || ultima.naoAvaliados || [];
    acumulado.push(...linhas);
    totalDePaginas = Math.max(1, ultima.pagina.totalDePaginas);
    numero += 1;
    if (aoProgredir) {
      aoProgredir(numero, totalDePaginas, acumulado.length, ultima.pagina.totalDeElementos);
    }
  } while (numero < totalDePaginas);

  return {
    linhas: acumulado, pagina: ultima.pagina, filtro: ultima.filtro, natureza: ultima.natureza,
    toleranciaDeValor: ultima.toleranciaDeValor,
  };
}

/* ---------------------------------------------------------------------------
   O cliente da conferencia.

   Separado de js/api.js de proposito. Aquele e o cliente da Etapa 9: fala com
   os quatro GET de leitura e tem escrito, no topo, que nao existe chamada de
   escrita ali. Continua verdade para ele. Este fala com a porta de escrita que
   a etapa de conferencia abriu — uma so, POST /api/analises — e com as leituras
   que dependem dela.

   Importar catalogo e tratar achado continuam fora: o primeiro decide o que o
   sistema afirma sobre a norma, o segundo e ato de pessoa identificada, e nao
   ha autenticacao aqui.
   --------------------------------------------------------------------------- */

/** Falha que a pagina sabe explicar para quem esta olhando. */
export class FalhaDaApi extends Error {
  constructor(mensagem, detalhe, situacao) {
    super(mensagem);
    this.name = 'FalhaDaApi';
    this.detalhe = detalhe || null;
    this.situacao = situacao || 0;
  }
}

const INVOCACAO =
  'java -Dspring.profiles.active=api -jar auditoria-ibs-cbs-<versao>.jar servir';

const SEM_API =
  'Nao foi possivel falar com o sistema. Ele sobe com o perfil "api" ativo e escuta so em '
  + '127.0.0.1.';

/** Le a resposta uma vez e devolve corpo interpretado, ou lanca explicando. */
async function interpretar(resposta, caminho) {
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
      : `O sistema respondeu ${resposta.status} para ${caminho}.`;
    throw new FalhaDaApi(mensagem, json && json.erro ? json.erro : null, resposta.status);
  }
  if (json === null) {
    throw new FalhaDaApi(`O sistema respondeu sem corpo para ${caminho}.`);
  }
  return json;
}

async function pegar(caminho) {
  let resposta;
  try {
    resposta = await fetch(caminho, { headers: { Accept: 'application/json' } });
  } catch (semRede) {
    throw new FalhaDaApi(SEM_API, INVOCACAO);
  }
  return interpretar(resposta, caminho);
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

/* --- a unica escrita ------------------------------------------------------ */

/**
 * Envia um .xml avulso ou um .zip com varios.
 *
 * O nome do arquivo vai junto porque e ele que decide como o pacote e tratado
 * do outro lado. O progresso do envio nao e acompanhado: fetch nao o expoe, e
 * inventar uma barra que anda sozinha seria pior que dizer "enviando".
 */
export async function analisar(arquivo) {
  const formulario = new FormData();
  formulario.append('arquivo', arquivo, arquivo.name);

  let resposta;
  try {
    resposta = await fetch('/api/analises', {
      method: 'POST',
      body: formulario,
      headers: { Accept: 'application/json' },
    });
  } catch (semRede) {
    throw new FalhaDaApi(SEM_API, INVOCACAO);
  }
  return interpretar(resposta, '/api/analises');
}

/* --- leituras da conferencia ---------------------------------------------- */

export function analise(id) {
  return pegar(`/api/analises/${encodeURIComponent(id)}`);
}

export function produtos(id, pagina, tamanho) {
  return pegar(`/api/analises/${encodeURIComponent(id)}/produtos${consulta({ pagina, tamanho })}`);
}

export function produto(id, endereco) {
  return pegar(
    `/api/analises/${encodeURIComponent(id)}/produtos/${encodeURIComponent(endereco)}`,
  );
}

export function grupos(id, ordem) {
  return pegar(`/api/analises/${encodeURIComponent(id)}/grupos${consulta({ ordem })}`);
}

export function produtosDoGrupo(id, chave, pagina, tamanho) {
  const busca = consulta({
    ncm: chave.ncm,
    cClassTrib: chave.cClassTrib,
    situacao: chave.situacao,
    pagina,
    tamanho,
  });
  return pegar(`/api/analises/${encodeURIComponent(id)}/grupos/produtos${busca}`);
}

/**
 * A base tributaria numa data.
 *
 * A data e obrigatoria e vem sempre de quem pergunta. Nao ha aqui nenhum
 * `new Date()` alimentando este parametro por padrao: a D003 admitiu este caso
 * de uso com data explicita, e um padrao silencioso de "hoje" traria de volta o
 * problema que ela fechou.
 */
export function baseTributaria(data, ncm, cClassTrib) {
  return pegar(`/api/base-tributaria${consulta({ data, ncm, cClassTrib })}`);
}

/** O historico usa a listagem de execucoes da Etapa 8, que ja existe. */
export function execucoes(limite) {
  return pegar(`/api/execucoes${consulta({ limite })}`);
}

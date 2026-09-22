/* ---------------------------------------------------------------------------
   Os quatro GET da API de leitura. Nada mais.

   Nao existe aqui nenhuma chamada de escrita, e isso nao e omissao: a Etapa 8
   nao expoe nenhuma. Auditar tem efeito colateral gravado, importar catalogo
   decide o que o sistema afirma sobre a norma, e tratar achado e ato de uma
   pessoa identificada — os tres continuam na CLI, atras de quem tem acesso a
   maquina, e nao atras de uma porta sem autenticacao.
   --------------------------------------------------------------------------- */

/** Teto de pagina aceito pela API (PaginaExposta.TAMANHO_MAXIMO). */
export const TAMANHO_MAXIMO = 500;

/** Falha que a pagina sabe explicar para quem esta olhando. */
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

/**
 * Busca TODAS as paginas de um recorte.
 *
 * <h2>Por que a pagina inteira e trazida para o cliente</h2>
 *
 * A API pagina e ordena por severidade, o que e a ordem certa para ela: e
 * deterministica, e paginar sobre ordem instavel devolveria a mesma linha em
 * duas paginas e nenhuma vez em outra.
 *
 * Mas a tela ordena por VALOR EM RISCO e agrupa por (NCM, cClassTrib, regra), e
 * as duas coisas sao globais: o grupo de maior valor pode ter uma ocorrencia na
 * primeira pagina e duas na ultima. Ordenar ou agrupar sobre um recorte daria
 * um resultado que parece certo e esta errado — o modo de falha exato que esta
 * tela existe para evitar. Entao busca-se tudo, em paginas de 500, e o
 * progresso e mostrado enquanto isso.
 */
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

  return { linhas: acumulado, pagina: ultima.pagina, filtro: ultima.filtro };
}

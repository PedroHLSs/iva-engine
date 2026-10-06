import { escrever, ler } from '../sessao.js';

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

export async function analisar(arquivo) {
  const formulario = new FormData();
  formulario.append('arquivo', arquivo, arquivo.name);
  return escrever('POST', '/api/analises', formulario);
}

export async function corrigir(analiseId, arquivo) {
  const formulario = new FormData();
  formulario.append('arquivo', arquivo, arquivo.name);
  return escrever('POST', `/api/analises/${encodeURIComponent(analiseId)}/correcoes`, formulario);
}

export function naoConcluidas(analiseId, pagina, tamanho) {
  return pegar(`/api/execucoes/${encodeURIComponent(analiseId)}/nao-avaliados${consulta({ pagina, tamanho })}`);
}

export function autoria(analiseId) {
  return pegar(`/api/analises/${encodeURIComponent(analiseId)}/autoria`);
}

export function historico(filtros) {
  return pegar(`/api/analises${consulta(filtros)}`);
}

export function previaDaMedicao() {
  return pegar('/api/acuracia/previa');
}

export async function medirAcuracia(notas, gabarito, cargaEsperada) {
  const formulario = new FormData();
  formulario.append('notas', notas, notas.name);
  formulario.append('gabarito', gabarito, gabarito.name);
  formulario.append('cargaEsperada', cargaEsperada);
  return escrever('POST', '/api/acuracia', formulario);
}

export function vinculos(analiseId) {
  return ler(`/api/analises/${encodeURIComponent(analiseId)}/vinculos`);
}

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

export function baseTributaria(data, ncm, cClassTrib) {
  return pegar(`/api/base-tributaria${consulta({ data, ncm, cClassTrib })}`);
}

export function execucoes(limite) {
  return pegar(`/api/execucoes${consulta({ limite })}`);
}

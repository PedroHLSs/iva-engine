/* ---------------------------------------------------------------------------
   O detalhe de um produto: a tela em que a conferencia de fato acontece.

   Seis blocos, na ordem em que a pergunta se responde:

     1. que produto e este, e a que situacao as regras chegaram;
     2. o que o documento declarou, campo a campo;
     3. a descricao da nota ao lado da descricao que o catalogo da ao NCM;
     4. que tratamento a base normativa indica — IBS e CBS em quadros separados;
     5. o declarado e o indicado lado a lado, SEM veredito proprio;
     6. por que cada regra chegou ao que chegou.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import { data, documentoCurto, rotuloDaRegra } from '../../formato.js';
import * as api from '../api.js';
import {
  avisoDeUso, cabecalhoDoResultado, leituraDoCatalogo, quadroDeEstados, selo, valorOuMotivo,
} from '../pecas.js';
import { enderecoDoResultado } from '../roteador.js';

export async function desenhar(raiz, parametros, rota) {
  const resposta = await api.produto(rota.analiseId, rota.endereco);
  const produto = resposta.produto;

  trocar(raiz, [
    cabecalhoDoResultado(
      'Produto ' + produto.numeroItem + ' da nota ' + documentoCurto(resposta.documento),
      null,
      resposta.natureza,
    ),

    blocoDaSituacao(produto, resposta.documento),
    blocoDoDeclarado(resposta.declarado),
    blocoDasDescricoes(resposta.descricoes),
    blocoDoTratamento(resposta.tratamento),
    blocoDaComparacao(resposta.comparacao),
    blocoDosPassos(resposta.passos),

    el('div', { classe: 'acoes' }, [
      el('a', {
        classe: 'botao',
        href: enderecoDoResultado(rota.analiseId),
        texto: 'Voltar ao resultado',
      }),
    ]),
    avisoDeUso(resposta.aviso),
  ]);
}

/* --- 1. situacao ---------------------------------------------------------- */

function blocoDaSituacao(produto, documento) {
  return el('section', { classe: 'bloco' }, [
    el('div', { classe: 'situacao-cabeca' }, [
      selo(produto.situacao, produto.rotuloDaSituacao, produto.explicacaoDaSituacao),
      el('p', { texto: produto.explicacaoDaSituacao }),
    ]),
    el('p', { classe: 'nota', texto: produto.comoASituacaoFoiObtida }),

    produto.reprocessadoDepoisDestaAnalise
      ? el('div', { classe: 'aviso', texto: produto.avisoDeReprocessamento })
      : null,

    quadroDeEstados(produto.verificacoesPorEstado, 'Verificacoes deste produto'),

    el('dl', { classe: 'identificacao' }, [
      el('dt', { texto: 'nota' }),
      el('dd', { texto: documentoCurto(documento) }),
      el('dt', { texto: 'emissao' }),
      el('dd', { texto: data(documento.dataEmissao) }),
      el('dt', { texto: 'UF do emitente' }),
      el('dd', { texto: documento.ufEmitente }),
      el('dt', { texto: 'chave de acesso' }),
      el('dd', {}, [valorOuMotivo(documento.chaveAcesso, documento.motivoDaChaveOmitida)]),
    ]),
  ]);
}

/* --- 2. o que veio no XML ------------------------------------------------- */

/**
 * Campo a campo, com ausencia escrita.
 *
 * Ausente e zero sao estados diferentes desde a Etapa 1, e e aqui que a
 * distincao costuma morrer: um traco na celula vira zero na cabeca de quem le.
 * Por isso todo campo sem valor sai com o motivo, e nao em branco.
 */
function blocoDoDeclarado(declarado) {
  const linhas = declarado.campos.map((campo) => el('tr', {}, [
    el('th', { scope: 'row', texto: campo.campo }),
    el('td', {}, [valorOuMotivo(campo.valor, campo.motivoDaAusencia)]),
  ]));

  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'O que o documento declarou' }),
    el('div', { classe: 'rolagem' }, [
      el('table', { classe: 'tabela tabela-campos' }, [el('tbody', {}, linhas)]),
    ]),
  ]);
}

/* --- 3. as duas descricoes ------------------------------------------------ */

/**
 * A descricao da nota ao lado da do catalogo.
 *
 * Divergencia entre as duas costuma indicar classificacao errada — informacao
 * que nenhuma das duas da sozinha. O sistema NAO as compara: nenhuma das regras
 * cadastradas examina texto, e um veredito automatico aqui nao teria de onde
 * sair.
 */
function blocoDasDescricoes(descricoes) {
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Descricao do produto' }),
    el('div', { classe: 'lado-a-lado' }, [
      el('div', { classe: 'lado' }, [
        el('h3', { texto: 'na nota' }),
        valorOuMotivo(descricoes.naNota, descricoes.motivoSemDescricaoNaNota),
      ]),
      el('div', { classe: 'lado' }, [
        el('h3', { texto: 'no catalogo, para o NCM declarado' }),
        valorOuMotivo(descricoes.noCatalogo, descricoes.motivoSemDescricaoNoCatalogo),
      ]),
    ]),
    el('p', { classe: 'nota', texto: descricoes.comoLer }),
  ]);
}

/* --- 4. tratamento identificado ------------------------------------------- */

function blocoDoTratamento(tratamento) {
  const familias = new Map();
  for (const doTributo of tratamento.porTributo) {
    if (!familias.has(doTributo.familia)) {
      familias.set(doTributo.familia, []);
    }
    familias.get(doTributo.familia).push(doTributo);
  }

  const quadros = [];
  for (const [familia, tributos] of familias) {
    quadros.push(el('div', { classe: 'quadro-tributo' }, [
      el('h3', { texto: familia }),
      ...tributos.map(quadroDeUmTributo),
    ]));
  }

  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Tratamento indicado pela base normativa' }),
    el('p', { classe: 'nota' }, [
      'Resolvido na carga ',
      el('span', { classe: 'mono', texto: tratamento.versaoDoCatalogo }),
      ', na data de emissao do documento (',
      el('span', { classe: 'mono', texto: data(tratamento.dataDeReferencia) }),
      '). As duas coordenadas andam juntas: a mesma data resolve diferente em duas cargas.',
    ]),

    el('h3', { texto: 'NCM declarado' }),
    leituraDoCatalogo(tratamento.descricaoDoNcm, (registros) =>
      el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
        el('span', { classe: 'mono', texto: registro.ncm }),
        ' — ',
        el('span', { texto: registro.descricao }),
        referencia(registro.referencia),
      ])))),

    el('h3', { texto: 'Enquadramento' }),
    leituraDoCatalogo(tratamento.enquadramentos, (registros) =>
      el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
        el('strong', { texto: registro.anexo }),
        ' — ',
        el('span', { texto: registro.tipoDeTratamento }),
        referencia(registro.referencia),
      ])))),

    el('h3', { texto: 'Classificacao tributaria declarada' }),
    leituraDoCatalogo(tratamento.classificacao, (registros) =>
      el('div', {}, registros.map(quadroDaClassificacao))),

    /*
     * IBS e CBS em quadros proprios porque podem divergir: um produto pode estar
     * coerente num e nao no outro, e uma linha unica esconderia exatamente o caso
     * que interessa conferir.
     */
    el('h3', { texto: 'Aliquotas vigentes na data' }),
    el('div', { classe: 'tributos' }, quadros),
    el('p', {
      classe: 'nota',
      texto: 'As parcelas estadual e municipal do IBS aparecem separadas, cada uma com a propria '
        + 'vigencia e a propria fonte. O sistema nao as soma: somar produziria um percentual que '
        + 'nenhuma linha da carga declara.',
    }),
  ]);
}

function quadroDeUmTributo(doTributo) {
  return el('div', { classe: 'tributo' }, [
    el('p', { classe: 'tributo-rotulo', texto: doTributo.rotulo }),
    leituraDoCatalogo(doTributo.aliquotas, (aliquotas) =>
      el('ul', { classe: 'lista-catalogo' }, aliquotas.map((aliquota) => el('li', {}, [
        el('span', { classe: 'mono percentual', texto: aliquota.percentual }),
        ' — ',
        el('span', { texto: aliquota.abrangencia }),
        referencia(aliquota.referencia),
      ])))),
  ]);
}

function quadroDaClassificacao(classificacao) {
  return el('dl', { classe: 'identificacao' }, [
    el('dt', { texto: 'codigo' }),
    el('dd', { classe: 'mono', texto: classificacao.codigo }),
    el('dt', { texto: 'dispositivo citado pela fonte' }),
    el('dd', { texto: classificacao.dispositivoLegal }),
    el('dt', { texto: 'CST admitidos' }),
    el('dd', {}, [
      classificacao.cstsAdmitidos.length
        ? el('span', { classe: 'mono', texto: classificacao.cstsAdmitidos.join(', ') })
        : valorOuMotivo(null, classificacao.motivoSemCstAdmitido),
    ]),
    el('dt', { texto: 'benefício declarado' }),
    el('dd', { texto: classificacao.indicadorDeBeneficio ? 'sim' : 'nao' }),
    el('dt', { texto: 'redução' }),
    el('dd', {}, [
      valorOuMotivo(classificacao.percentualReducao, classificacao.motivoSemReducao),
    ]),
    el('dt', { texto: 'campos condicionados' }),
    el('dd', {}, [
      classificacao.camposObrigatoriosCondicionados.length
        ? el('span', { texto: classificacao.camposObrigatoriosCondicionados.join(', ') })
        : valorOuMotivo(null, classificacao.motivoSemCampoCondicionado),
    ]),
    el('dt', { texto: 'vigência e fonte' }),
    el('dd', {}, [referencia(classificacao.referencia)]),
  ]);
}

/** Vigencia e fonte, exatamente como a carga as declarou. */
function referencia(ref) {
  if (!ref) {
    return el('span', { classe: 'ausente', texto: 'a resposta nao trouxe a referencia' });
  }
  const fim = ref.vigenciaFim
    ? data(ref.vigenciaFim)
    : (ref.motivoDaVigenciaSemFim || 'sem fim declarado');
  return el('p', { classe: 'referencia' }, [
    el('span', { texto: 'de ' + data(ref.vigenciaInicio) + ' ate ' }),
    ref.vigenciaFim
      ? el('span', { texto: fim })
      : el('span', { classe: 'ausente', texto: fim }),
    el('span', { texto: ' — fonte: ' }),
    el('span', { texto: ref.fonteNormativa }),
  ]);
}

/* --- 5. declarado x indicado ---------------------------------------------- */

function blocoDaComparacao(comparacao) {
  const linhas = comparacao.linhas.map((linha) => el('tr', {}, [
    el('th', { scope: 'row', texto: linha.campo }),
    el('td', {}, [valorOuMotivo(linha.declarado, linha.motivoDoNaoDeclarado)]),
    el('td', {}, [
      leituraDoCatalogo(linha.indicado, (valores) =>
        el('span', { classe: 'mono', texto: valores.join(' | ') })),
    ]),
  ]));

  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Declarado e indicado, lado a lado' }),
    el('div', { classe: 'rolagem' }, [
      el('table', { classe: 'tabela' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { texto: 'campo' }),
          el('th', { texto: 'declarado no documento' }),
          el('th', { texto: 'indicado pela base normativa' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
    /*
     * Este quadro nao escreve "confere" nem "nao confere". Quem julga sao as
     * sete regras, e o julgamento delas ja esta na situacao acima. Um veredito
     * proprio aqui seria um oitavo juizo, mais fraco que os outros sete.
     */
    el('p', { classe: 'nota quem-julga', texto: comparacao.quemJulga }),
  ]);
}

/* --- 6. por que este resultado -------------------------------------------- */

function blocoDosPassos(passos) {
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Por que este resultado' }),
    el('ol', { classe: 'passos' }, passos.map(passo)),
  ]);
}

function passo(item) {
  return el('li', { classe: 'passo' }, [
    el('div', { classe: 'passo-cabeca' }, [
      rotuloDaRegra(item),
      selo(item.estado, item.rotuloDoEstado, item.explicacaoDoEstado),
    ]),
    el('p', { classe: 'nota' }, [
      'versao da regra: ',
      valorOuMotivo(item.regraVersao, item.motivoDaVersaoAusente),
    ]),
    explicacao(item.explicacao),
  ]);
}

/**
 * Tres procedencias, tres desenhos.
 *
 * Apontamento explica pelas evidencias que gravou; pendencia, pelo motivo que a
 * propria regra escreveu; conforme, pela conta que o derivou. Desenhar os tres
 * com a mesma frase generica seria a forma mais facil de a tela parecer completa
 * sem dizer nada.
 */
function explicacao(dados) {
  if (dados.tipo === 'APONTAMENTO') {
    return el('div', { classe: 'explicacao' }, [
      el('div', { classe: 'rolagem' }, [
        el('table', { classe: 'tabela tabela-evidencias' }, [
          el('thead', {}, [el('tr', {}, [
            el('th', { texto: 'campo examinado' }),
            el('th', { texto: 'encontrado' }),
            el('th', { texto: 'esperado' }),
            el('th', { texto: 'de onde saiu' }),
          ])]),
          el('tbody', {}, dados.evidencias.map((evidencia) => el('tr', {}, [
            el('td', { texto: evidencia.campoAnalisado }),
            el('td', {}, [
              valorOuMotivo(evidencia.valorEncontrado, evidencia.motivoDoEncontradoAusente),
            ]),
            el('td', {}, [
              valorOuMotivo(evidencia.valorEsperado, evidencia.motivoDoEsperadoAusente),
            ]),
            el('td', { classe: 'origem', texto: evidencia.origem }),
          ]))),
        ]),
      ]),
      el('p', {}, [
        el('strong', { texto: 'Fundamento: ' }),
        el('span', { texto: dados.fundamentacao.fonteNormativa }),
      ]),
      referencia(dados.fundamentacao),
      el('p', {}, [
        el('strong', { texto: 'Valor em risco: ' }),
        valorOuMotivo(dados.valorEmRisco, dados.motivoDoValorAusente),
      ]),
    ]);
  }
  return el('p', { classe: 'explicacao texto-normativo', texto: dados.texto });
}

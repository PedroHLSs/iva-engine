/* ---------------------------------------------------------------------------
   Resultado de uma nota: identificacao, os quatro numeros, e os produtos.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import { data, documentoCurto, inteiro } from '../../formato.js';
import * as api from '../api.js';
import {
  avisoDeUso, blocoDaLeitura, cabecalhoDoResultado, produtosComPendencia,
  quadroDeEstados, selo, valorOuMotivo,
} from '../pecas.js';
import { enderecoDeEnvio, enderecoDoProduto } from '../roteador.js';

const TAMANHO_DA_PAGINA = 50;

export async function desenhar(raiz, resposta, rota) {
  const corpo = el('div', {});

  trocar(raiz, [
    cabecalhoDoResultado(
      'Resultado da nota',
      null,
      resposta.natureza,
    ),
    corpo,
  ]);

  await desenharPagina(corpo, resposta, rota, 0);
}

async function desenharPagina(corpo, resposta, rota, pagina) {
  trocar(corpo, [el('p', { classe: 'carregando', texto: 'Carregando os produtos...' })]);

  const listagem = await api.produtos(rota.analiseId, pagina, TAMANHO_DA_PAGINA);
  const conferencia = resposta.conferencia;
  const primeiro = listagem.produtos[0];

  trocar(corpo, [
    primeiro ? identificacaoDoDocumento(primeiro.documento) : null,

    quadroDeEstados(conferencia.produtosPorSituacao, 'Situacao dos produtos'),
    produtosComPendencia(
      conferencia.produtosComAlgumaVerificacaoNaoConcluida,
      conferencia.quantidadeDeProdutos,
    ),
    el('p', { classe: 'nota', texto: conferencia.comoFoiObtido }),

    quadroDeEstados(conferencia.verificacoesPorEstado, 'Verificacoes, regra a regra'),

    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Produtos' }),
      tabelaDeProdutos(listagem.produtos, rota.analiseId),
      paginacao(listagem.pagina, (destino) => desenharPagina(corpo, resposta, rota, destino)),
    ]),

    blocoDaLeitura(resposta.leitura),

    el('div', { classe: 'acoes' }, [
      el('a', { classe: 'botao', href: enderecoDeEnvio(), texto: 'Enviar outra nota' }),
    ]),
    avisoDeUso(resposta.aviso),
  ]);
}

/**
 * A nota, identificada sem identificar ninguem.
 *
 * Modelo, serie, numero, data e UF localizam o documento no sistema da empresa.
 * A chave de acesso so aparece se a instalacao a expuser — os digitos do meio
 * dela sao o CNPJ do emitente.
 */
function identificacaoDoDocumento(documento) {
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Documento' }),
    el('dl', { classe: 'identificacao' }, [
      el('dt', { texto: 'modelo / serie / numero' }),
      el('dd', { texto: documentoCurto(documento) }),
      el('dt', { texto: 'emissao' }),
      el('dd', { texto: data(documento.dataEmissao) }),
      el('dt', { texto: 'UF do emitente' }),
      el('dd', { texto: documento.ufEmitente }),
      el('dt', { texto: 'chave de acesso' }),
      el('dd', {}, [valorOuMotivo(documento.chaveAcesso, documento.motivoDaChaveOmitida)]),
      el('dt', { texto: 'pseudonimo' }),
      el('dd', { classe: 'mono pseudonimo', texto: documento.pseudonimo }),
    ]),
  ]);
}

function tabelaDeProdutos(produtos, analiseId) {
  if (!produtos.length) {
    return el('p', { classe: 'tabela-vazia', texto: 'Esta pagina nao alcanca nenhum produto.' });
  }

  const linhas = produtos.map((produto) => el('tr', {}, [
    el('td', { texto: String(produto.numeroItem) }),
    el('td', {}, [valorOuMotivo(produto.ncm, produto.motivoDoNcmAusente)]),
    el('td', {}, [valorOuMotivo(produto.cClassTrib, produto.motivoDoClassTribAusente)]),
    el('td', { classe: 'numero mono', texto: produto.valorDoProduto }),
    el('td', {}, [
      selo(produto.situacao, produto.rotuloDaSituacao, produto.explicacaoDaSituacao),
      produto.reprocessadoDepoisDestaAnalise
        ? el('p', { classe: 'nota aviso-reprocesso', texto: produto.avisoDeReprocessamento })
        : null,
    ]),
    el('td', {}, [
      el('a', { href: enderecoDoProduto(analiseId, produto.endereco), texto: 'detalhe' }),
    ]),
  ]));

  return el('div', { classe: 'rolagem' }, [
    el('table', { classe: 'tabela' }, [
      el('thead', {}, [el('tr', {}, [
        el('th', { texto: 'item' }),
        el('th', { texto: 'NCM' }),
        el('th', { texto: 'tratamento declarado (cClassTrib)' }),
        el('th', { classe: 'numero', texto: 'valor do produto' }),
        el('th', { texto: 'situacao' }),
        el('th', { texto: '' }),
      ])]),
      el('tbody', {}, linhas),
    ]),
    el('p', {
      classe: 'nota',
      texto: 'A coluna de tratamento traz o codigo que o documento declarou. O tratamento que a '
        + 'base normativa indica e resolvido por produto, na data de emissao desta nota e contra '
        + 'a carga que esta analise registrou, e esta no detalhe de cada um.',
    }),
  ]);
}

function paginacao(pagina, aoIr) {
  if (pagina.totalDePaginas <= 1) {
    return null;
  }
  return el('div', { classe: 'acoes paginacao' }, [
    el('button', {
      classe: 'botao',
      disabled: pagina.numero === 0 ? 'disabled' : null,
      texto: 'anterior',
      aoClicar: () => aoIr(pagina.numero - 1),
    }),
    el('span', {
      classe: 'nota',
      texto: 'pagina ' + inteiro(pagina.numero + 1) + ' de ' + inteiro(pagina.totalDePaginas)
        + ' — ' + inteiro(pagina.totalDeElementos) + ' produto(s)',
    }),
    el('button', {
      classe: 'botao',
      disabled: pagina.numero + 1 >= pagina.totalDePaginas ? 'disabled' : null,
      texto: 'proxima',
      aoClicar: () => aoIr(pagina.numero + 1),
    }),
  ]);
}

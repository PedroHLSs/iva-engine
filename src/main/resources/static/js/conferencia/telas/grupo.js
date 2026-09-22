/* ---------------------------------------------------------------------------
   As notas e os itens que compoem um grupo.

   E o caminho de volta ao concreto: do erro de parametrizacao para as notas em
   que ele aparece, e de la para o detalhe de cada produto.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import { data, documentoCurto, inteiro } from '../../formato.js';
import * as api from '../api.js';
import { avisoDeUso, cabecalhoDoResultado, selo, valorOuMotivo } from '../pecas.js';
import { enderecoDoProduto, enderecoDoResultado } from '../roteador.js';

const TAMANHO_DA_PAGINA = 50;

export async function desenhar(raiz, parametros, rota) {
  const chave = {
    ncm: rota.parametros.get('ncm'),
    cClassTrib: rota.parametros.get('cClassTrib'),
    situacao: rota.parametros.get('situacao'),
  };
  const corpo = el('div', {});
  trocar(raiz, [corpo]);
  await desenharPagina(corpo, rota.analiseId, chave, 0);
}

async function desenharPagina(corpo, analiseId, chave, pagina) {
  trocar(corpo, [el('p', { classe: 'carregando', texto: 'Carregando...' })]);

  const resposta = await api.produtosDoGrupo(analiseId, chave, pagina, TAMANHO_DA_PAGINA);
  const grupo = resposta.grupo;

  trocar(corpo, [
    cabecalhoDoResultado('Produtos do grupo', null, resposta.natureza),

    el('section', { classe: 'bloco' }, [
      el('div', { classe: 'grupo-chave' }, [
        el('span', { classe: 'grupo-rotulo', texto: 'NCM' }),
        valorOuMotivo(grupo.ncm, grupo.motivoDoNcmAusente),
        el('span', { classe: 'grupo-rotulo', texto: 'cClassTrib' }),
        valorOuMotivo(grupo.cClassTrib, grupo.motivoDoClassTribAusente),
        selo(grupo.situacao, grupo.rotuloDaSituacao, grupo.explicacaoDaSituacao),
      ]),
      el('p', { classe: 'grupo-nivel', texto: grupo.rotuloDoNivel }),
      el('p', {}, [
        el('strong', { texto: inteiro(grupo.quantidadeDeProdutos) }),
        ' produto(s) em ' + inteiro(grupo.quantidadeDeNotas) + ' nota(s), ',
        el('strong', { classe: 'mono', texto: grupo.valorDosProdutos }),
        el('span', { classe: 'rotulo-do-valor', texto: ' ' + grupo.rotuloDoValorDosProdutos }),
      ]),
    ]),

    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Notas e itens' }),
      tabela(resposta.produtos, analiseId),
      paginacao(resposta.pagina, (destino) =>
        desenharPagina(corpo, analiseId, chave, destino)),
    ]),

    el('div', { classe: 'acoes' }, [
      el('a', {
        classe: 'botao',
        href: enderecoDoResultado(analiseId),
        texto: 'Voltar aos grupos',
      }),
    ]),
    avisoDeUso(resposta.aviso),
  ]);
}

function tabela(produtos, analiseId) {
  if (!produtos.length) {
    return el('p', { classe: 'tabela-vazia', texto: 'Esta pagina nao alcanca nenhum produto.' });
  }

  const linhas = produtos.map((produto) => el('tr', {}, [
    el('td', { texto: documentoCurto(produto.documento) }),
    el('td', { texto: data(produto.documento.dataEmissao) }),
    el('td', { texto: String(produto.numeroItem) }),
    el('td', { classe: 'numero mono', texto: produto.valorDoProduto }),
    el('td', {}, [selo(produto.situacao, produto.rotuloDaSituacao, produto.explicacaoDaSituacao)]),
    el('td', {}, [
      el('a', { href: enderecoDoProduto(analiseId, produto.endereco), texto: 'detalhe' }),
    ]),
  ]));

  return el('div', { classe: 'rolagem' }, [
    el('table', { classe: 'tabela' }, [
      el('thead', {}, [el('tr', {}, [
        el('th', { texto: 'nota' }),
        el('th', { texto: 'emissao' }),
        el('th', { texto: 'item' }),
        el('th', { classe: 'numero', texto: 'valor do produto' }),
        el('th', { texto: 'situacao' }),
        el('th', { texto: '' }),
      ])]),
      el('tbody', {}, linhas),
    ]),
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
      texto: 'pagina ' + inteiro(pagina.numero + 1) + ' de ' + inteiro(pagina.totalDePaginas),
    }),
    el('button', {
      classe: 'botao',
      disabled: pagina.numero + 1 >= pagina.totalDePaginas ? 'disabled' : null,
      texto: 'proxima',
      aoClicar: () => aoIr(pagina.numero + 1),
    }),
  ]);
}

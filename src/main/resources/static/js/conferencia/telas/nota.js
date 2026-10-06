import { el, trocar } from '../../dom.js';
import { data, documentoCurto, inteiro } from '../../formato.js';
import * as api from '../api.js';
import { selo, valorOuMotivo } from '../pecas.js';
import { botaoDeInformacao } from '../../painel.js';
import { enderecoDoProduto } from '../roteador.js';

const TAMANHO_DA_PAGINA = 50;

export async function painelDeProdutos(painel, analiseId, pagina = 0) {
  trocar(painel, [el('p', { classe: 'carregando', role: 'status', texto: 'Carregando os produtos...' })]);
  const listagem = await api.produtos(analiseId, pagina, TAMANHO_DA_PAGINA);
  trocar(painel, [
    el('h2', { texto: 'Produtos' }),
    tabelaDeProdutos(listagem.produtos, analiseId),
    paginacao(listagem.pagina, (destino) => painelDeProdutos(painel, analiseId, destino)),
  ]);
}

export function identificacaoDoDocumento(documento) {
  const par = (chave, conteudo, classe) => el('div', { classe: 'meta-par' }, [
    el('dt', { texto: chave }),
    el('dd', { classe: classe || null }, [].concat(conteudo)),
  ]);
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Documento' }),
    el('dl', { classe: 'metadados' }, [
      par('modelo / serie / numero', documentoCurto(documento)),
      par('emissao', data(documento.dataEmissao)),
      par('UF do emitente', valorOuMotivo(documento.ufEmitente, null)),
      par('chave de acesso', chaveDeAcesso(documento), documento.chaveAcesso ? 'mono quebra-tudo' : null),
      par('pseudonimo', documento.pseudonimo, 'mono quebra-tudo'),
    ]),
  ]);
}

export function chaveDeAcesso(documento) {
  if (documento.chaveAcesso) {
    return documento.chaveAcesso;
  }
  if (!documento.motivoDaChaveOmitida) {
    return valorOuMotivo(null, null);
  }
  const info = botaoDeInformacao('Por que a chave nao aparece', el('p', { texto: documento.motivoDaChaveOmitida }));
  info.botao.textContent = '?';
  return [
    el('span', { classe: 'valor-com-info' }, [el('span', { classe: 'ausente', texto: 'nao exposta nesta instalacao' }), info.botao]),
    info.balao,
  ];
}

function tabelaDeProdutos(produtos, analiseId) {
  if (!produtos.length) {
    return el('p', { classe: 'tabela-vazia', texto: 'Esta pagina nao alcanca nenhum produto.' });
  }

  const linhas = produtos.map((produto) => el('tr', {}, [
    el('td', { classe: 'coluna-situacao' }, [
      selo(produto.situacao, produto.rotuloDaSituacao, produto.explicacaoDaSituacao),
      produto.reprocessadoDepoisDestaAnalise
        ? el('p', { classe: 'nota aviso-reprocesso', texto: produto.avisoDeReprocessamento })
        : null,
    ]),
    el('td', { texto: String(produto.numeroItem) }),
    el('td', {}, [valorOuMotivo(produto.ncm, produto.motivoDoNcmAusente)]),
    el('td', {}, [valorOuMotivo(produto.cClassTrib, produto.motivoDoClassTribAusente)]),
    el('td', { classe: 'numero mono', texto: produto.valorDoProduto }),
    el('td', {}, [
      el('a', { classe: 'botao-detalhe', href: enderecoDoProduto(analiseId, produto.endereco), texto: 'detalhe' }),
    ]),
  ]));

  return el('div', { classe: 'rolagem' }, [
    el('table', { classe: 'tabela' }, [
      el('thead', {}, [el('tr', {}, [
        el('th', { scope: 'col', texto: 'situacao' }),
        el('th', { scope: 'col', texto: 'item' }),
        el('th', { scope: 'col', texto: 'NCM' }),
        el('th', { scope: 'col', texto: 'tratamento declarado (cClassTrib)' }),
        el('th', { scope: 'col', classe: 'numero', texto: 'valor do produto' }),
        el('th', { scope: 'col', texto: 'detalhe' }),
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

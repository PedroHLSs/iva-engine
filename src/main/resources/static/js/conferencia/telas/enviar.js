/* ---------------------------------------------------------------------------
   Tela inicial: uma acao, e so uma.

   Ela NAO abre com uma lista de analises anteriores. Quem chega aqui quer
   conferir uma nota, e uma lista de execucoes no lugar do campo de envio
   obriga a pessoa a procurar o botao antes de fazer a unica coisa que veio
   fazer. O historico existe, e esta a um clique — abaixo, discreto.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import * as api from '../api.js';
import { falha } from '../pecas.js';
import { enderecoDaBase, enderecoDoHistorico, enderecoDoResultado, irPara } from '../roteador.js';

const EXTENSOES_ACEITAS = '.xml,.zip';

/** O que a tela diz enquanto o arquivo sobe e o motor roda. */
const ENVIANDO = 'Lendo o arquivo e aplicando as regras cadastradas...';

export async function desenhar(raiz) {
  const situacao = el('p', { classe: 'estado-do-envio', role: 'status' });
  const erro = el('div', {});

  const campo = el('input', {
    type: 'file',
    id: 'arquivo',
    accept: EXTENSOES_ACEITAS,
    classe: 'campo-arquivo',
  });

  let ocupado = false;

  async function enviar(arquivo) {
    if (!arquivo || ocupado) {
      return;
    }
    ocupado = true;
    trocar(erro, []);
    situacao.textContent = ENVIANDO;

    try {
      const resultado = await api.analisar(arquivo);
      irPara(enderecoDoResultado(resultado.leitura.id));
    } catch (naoDeuCerto) {
      situacao.textContent = '';
      trocar(erro, [falha(naoDeuCerto)]);
    } finally {
      ocupado = false;
      campo.value = '';
    }
  }

  campo.addEventListener('change', () => enviar(campo.files && campo.files[0]));

  const area = el('div', { classe: 'area-de-envio' }, [
    el('p', { classe: 'area-titulo', texto: 'Arraste a nota aqui' }),
    el('p', { classe: 'area-ou', texto: 'ou' }),
    el('label', { classe: 'botao principal', for: 'arquivo', texto: 'Escolher arquivo' }),
    campo,
    el('p', {
      classe: 'nota',
      texto: 'Um .xml de NF-e ou NFC-e, ou um .zip com varios. Arquivo do pacote que nao puder '
        + 'ser lido nao interrompe os outros: ele e contado a parte, com o motivo.',
    }),
  ]);

  /*
   * Arrastar exige cancelar dragover: sem isso o navegador abre o arquivo numa
   * aba e a pagina some junto com o que a pessoa estava fazendo.
   */
  area.addEventListener('dragover', (evento) => {
    evento.preventDefault();
    area.classList.add('recebendo');
  });
  area.addEventListener('dragleave', () => area.classList.remove('recebendo'));
  area.addEventListener('drop', (evento) => {
    evento.preventDefault();
    area.classList.remove('recebendo');
    const arquivos = evento.dataTransfer && evento.dataTransfer.files;
    enviar(arquivos && arquivos[0]);
  });

  trocar(raiz, [
    el('h1', { texto: 'Conferir enquadramento de IBS e CBS' }),
    el('p', {
      classe: 'sub',
      texto: 'Envie a nota e o sistema mostra, produto a produto, que tratamento a base normativa '
        + 'cadastrada indica e se o que o documento declara corresponde a ele.',
    }),
    area,
    situacao,
    erro,
    el('div', { classe: 'atalhos-discretos' }, [
      el('a', { href: enderecoDoHistorico(), texto: 'Analises anteriores' }),
      el('a', { href: enderecoDaBase(), texto: 'Base tributaria carregada' }),
    ]),
    el('p', {
      classe: 'nota',
      texto: 'Formato .rar nao e aceito: nao ha biblioteca Java confiavel para ele. Compacte em '
        + '.zip.',
    }),
  ]);
}

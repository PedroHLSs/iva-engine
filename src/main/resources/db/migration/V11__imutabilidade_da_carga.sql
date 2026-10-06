-- ---------------------------------------------------------------------------
-- Imutabilidade da carga de catálogo depois de usada (Etapa 12, D013).
--
-- Nenhum dado normativo aqui. Três colunas novas em "carga_catalogo", e
-- gatilhos que recusam mexer no conteúdo de carga selada.
--
-- O SELO
--
-- "selada_em" é preenchida no momento em que a carga é ENTREGUE ao motor — não
-- quando a análise termina de gravar. Selar na gravação deixaria uma janela: a
-- análise lê a carga X, alguém edita X enquanto o motor roda, e a execução
-- grava "usei X" tendo avaliado o X antigo. Selar na entrega fecha a janela,
-- porque a entrega e a edição travam a mesma linha.
--
-- O selo só anda num sentido. Uma vez preenchida, a coluna não volta a nulo —
-- nem se as análises que usaram a carga forem apagadas por "recomecar-do-zero".
-- Relatórios exportados antes do recomeço continuam citando a versão, e ela
-- precisa continuar querendo dizer o mesmo conteúdo.
--
-- Carga selada é imutável. "Editar" produz carga nova com "derivada_de"
-- apontando para a original, e a original fica intacta.
--
-- POR QUE GATILHO, E NÃO SÓ O CÓDIGO
--
-- É a mesma função das restrições "check" do resto do esquema: barreira de
-- última instância. O código já recusa editar carga selada; o gatilho garante
-- que um caminho futuro que esqueça de perguntar também seja recusado.
-- ---------------------------------------------------------------------------

alter table carga_catalogo add column selada_em timestamptz;
alter table carga_catalogo add column derivada_de uuid;
alter table carga_catalogo add column alterada_em timestamptz;

alter table carga_catalogo add constraint carga_catalogo_derivada_de_fk
    foreign key (derivada_de) references carga_catalogo (id) on delete restrict;

-- Carga já usada por alguma execução gravada nasce selada. A data do selo é a
-- da primeira execução que a citou: é o momento mais antigo em que se sabe que
-- ela foi entregue ao motor.
update carga_catalogo carga
   set selada_em = (select min(execucao.data_hora)
                      from execucao_auditoria execucao
                     where execucao.versao_catalogo = carga.versao)
 where exists (select 1
                 from execucao_auditoria execucao
                where execucao.versao_catalogo = carga.versao);

-- Diz se a carga está selada. Carga que não existe mais não está selada: é o
-- caso da exclusão em cascata de um rascunho, em que a linha da carga some
-- antes das linhas filhas.
create function carga_esta_selada(carga uuid) returns boolean as $$
    select coalesce((select selada_em is not null from carga_catalogo where id = carga), false);
$$ language sql stable;

create function recusar_mudanca_em_carga_selada() returns trigger as $$
declare
    carga uuid;
begin
    if tg_op = 'DELETE' then
        carga := old.carga_id;
    else
        carga := new.carga_id;
    end if;
    if carga_esta_selada(carga) then
        raise exception 'A carga de catálogo % está selada: já foi entregue a uma análise, e o conteúdo dela não muda mais (% em %). Editar uma carga usada cria uma carga nova.', carga, tg_op, tg_table_name;
    end if;
    if tg_op = 'UPDATE' and old.carga_id <> new.carga_id and carga_esta_selada(old.carga_id) then
        raise exception 'A carga de catálogo % está selada: % em % recusado.', old.carga_id, tg_op, tg_table_name;
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger classificacao_tributaria_carga_selada
    before insert or update or delete on classificacao_tributaria
    for each row execute function recusar_mudanca_em_carga_selada();
create trigger registro_ncm_carga_selada
    before insert or update or delete on registro_ncm
    for each row execute function recusar_mudanca_em_carga_selada();
create trigger item_anexo_carga_selada
    before insert or update or delete on item_anexo
    for each row execute function recusar_mudanca_em_carga_selada();
create trigger aliquota_vigente_carga_selada
    before insert or update or delete on aliquota_vigente
    for each row execute function recusar_mudanca_em_carga_selada();
create trigger cobertura_catalogo_carga_selada
    before insert or update or delete on cobertura_catalogo
    for each row execute function recusar_mudanca_em_carga_selada();
create trigger natureza_da_carga_carga_selada
    before insert or update or delete on natureza_da_carga
    for each row execute function recusar_mudanca_em_carga_selada();

-- As duas tabelas filhas da classificação chegam à carga pela classificação.
create function recusar_mudanca_em_filha_de_classificacao_selada() returns trigger as $$
declare
    classificacao uuid;
begin
    if tg_op = 'DELETE' then
        classificacao := old.classificacao_id;
    else
        classificacao := new.classificacao_id;
    end if;
    if carga_esta_selada((select carga_id from classificacao_tributaria where id = classificacao)) then
        raise exception 'A classificação % pertence a uma carga de catálogo selada: % em % recusado.', classificacao, tg_op, tg_table_name;
    end if;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger classificacao_tributaria_cst_carga_selada
    before insert or update or delete on classificacao_tributaria_cst
    for each row execute function recusar_mudanca_em_filha_de_classificacao_selada();
create trigger classificacao_tributaria_campo_obrigatorio_carga_selada
    before insert or update or delete on classificacao_tributaria_campo_obrigatorio
    for each row execute function recusar_mudanca_em_filha_de_classificacao_selada();

-- A própria carga: selada não é apagada, o selo não é retirado, e a versão e a
-- data de importação não mudam.
create function recusar_mudanca_na_carga_selada() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        if old.selada_em is not null then
            raise exception 'A carga de catálogo "%" está selada e não pode ser excluída: ao menos uma análise foi feita contra ela.', old.versao;
        end if;
        return old;
    end if;
    if old.selada_em is not null then
        if new.selada_em is null or new.selada_em <> old.selada_em then
            raise exception 'O selo da carga de catálogo "%" não pode ser retirado nem trocado.', old.versao;
        end if;
        if new.versao <> old.versao or new.importado_em <> old.importado_em
           or new.derivada_de is distinct from old.derivada_de
           or new.alterada_em is distinct from old.alterada_em then
            raise exception 'A carga de catálogo "%" está selada, e a identificação dela não muda.', old.versao;
        end if;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger carga_catalogo_selada
    before update or delete on carga_catalogo
    for each row execute function recusar_mudanca_na_carga_selada();

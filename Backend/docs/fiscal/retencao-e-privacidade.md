# Retenção e privacidade dos dados fiscais

## Finalidades

Os dados fiscais são tratados para emissão, consulta, cancelamento, escrituração, atendimento a obrigações legais e regulatórias, suporte operacional e prevenção a fraudes. A trilha de auditoria identifica o responsável pelo ato para garantir responsabilização e integridade.

## Regras implementadas

- Somente documentos no estado `DIGITACAO`, ainda não submetidos ao fluxo fiscal, podem ser excluídos pela aplicação.
- Documentos rejeitados são preservados como evidência da tentativa, assim como documentos em processamento, autorizados, cancelados ou em contingência.
- A exclusão de um rascunho gera evento de auditoria separado, preservando quem executou a ação, a empresa, o identificador e a data, sem copiar o conteúdo do documento.
- Detalhes livres da auditoria removem e-mails, CPF/CNPJ e valores associados a senha, token, chave de API, CSC ou certificado. O ator permanece identificado porque essa é a finalidade da trilha de responsabilização.
- XML, protocolos, certificados e credenciais não devem ser incluídos em mensagens de log nem em detalhes livres de auditoria.

## Prazo e descarte

O sistema não presume um prazo legal único. O prazo aplicável depende do tipo documental, obrigação, ente tributante e eventos que suspendam ou interrompam a contagem. A organização controladora deve aprovar uma tabela de temporalidade com assessoria fiscal/jurídica antes de automatizar descarte.

Ao fim do prazo aprovado, o descarte deve abranger cópias primárias, índices e réplicas segundo o ciclo de backup, manter evidência mínima do descarte e respeitar retenções judiciais ou administrativas. Até existir essa configuração aprovada, o sistema adota preservação conservadora e não oferece exclusão de registros submetidos ao fluxo fiscal.

## Acesso e atendimento ao titular

O acesso deve seguir empresa e perfil autorizados, com auditoria de operações fiscais. Pedidos de titulares devem ser avaliados sem apagar registros cuja conservação seja necessária ao cumprimento de obrigação legal ou regulatória; quando cabível, devem ser aplicadas restrição de acesso, correção ou anonimização dos dados que não precisem permanecer identificáveis.

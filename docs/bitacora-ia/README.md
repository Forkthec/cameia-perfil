# Bitácora de IA

Esta carpeta guarda la **entrada** de cada decisión en la que una persona cambió el rumbo de una propuesta de la IA (alcance, arquitectura, seguridad, costo, calidad o proceso): el prompt armado con rol, contexto, tarea, condiciones y formato. La **salida** es la spec del repositorio (`specs/CM-NNN-*/spec.md`, sección de decisiones, con la decisión humana, el porqué y las alternativas descartadas); la bitácora la enlaza y no la copia.

Cada entrada es un archivo `NN_tema_prompt.md`, con `NN` consecutivo.

| Entrada | Decisión | Salida |
|---|---|---|
| [01_alinear-estandar-backend_prompt.md](01_alinear-estandar-backend_prompt.md) | Alinear los documentos y el estándar de los cuatro repositorios de Backend | [Spec de CM-283](../../specs/CM-283-AlinearEstandar/spec.md) |
| [02_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md](02_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md) | Ignorar `provenance` en el `PATCH` de información general (decisión D5) | [Spec de CM-54](../../specs/CM-54-informacion-general-resumen/spec.md) |
| [03_errores-de-protocolo-previsibles_prompt.md](03_errores-de-protocolo-previsibles_prompt.md) | Respuesta de Perfil ante formularios, consultas y multipart mal formados, `TRACE` e identidad con caracteres de control | [Spec de CM-335](../../specs/CM-335-ErroresDeProtocoloPrevisibles/spec.md) |
| [04_catalogo-de-roles-por-evento_prompt.md](04_catalogo-de-roles-por-evento_prompt.md) | Cómo llega el catálogo de roles de Perfil a Entrevista: instantánea completa publicada en cada arranque (D-RPL-08 a D-RPL-12) | Spec de CM-209 en `cameia-entrevista` (`specs/CM-209-ReplicaPerfilProfesional/spec.md`) |

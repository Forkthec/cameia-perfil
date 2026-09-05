\# cameia-perfil



Microservicio de Perfil Profesional de CAMEIA. Mantiene la información profesional del usuario, su procedencia y revisión, y los roles objetivo del MVP.



> \*\*Estado:\*\* repositorio creado para el Sprint 1. La base técnica corresponde a \[CM-102](https://f0rktech.atlassian.net/browse/CM-102); este documento separa el alcance vigente de capacidades históricas o futuras.



\## Alcance del Sprint 1



\- Selección del método de configuración: \[CM-16](https://f0rktech.atlassian.net/browse/CM-16).

\- Información general y resumen profesional: \[CM-17](https://f0rktech.atlassian.net/browse/CM-17).

\- Experiencia laboral y educación: \[CM-18](https://f0rktech.atlassian.net/browse/CM-18).

\- Habilidades, expectativas y finalización: \[CM-19](https://f0rktech.atlassian.net/browse/CM-19).

\- Gestión de roles objetivo: \[CM-20](https://f0rktech.atlassian.net/browse/CM-20).



La extracción automática de CV y las sugerencias de IA se mantienen como arquitectura prevista hasta que Jira las autorice expresamente.



\## Responsabilidades



\- Crear, editar y versionar el Perfil Profesional.

\- Mantener experiencia, educación, habilidades, expectativas y roles objetivo.

\- Registrar procedencia y estado de revisión de contenido asistido por IA.

\- Conservar únicamente el texto extraído de un CV cuando esa capacidad entre en alcance.

\- Publicar cambios del perfil mediante contratos aprobados.



No administra vacantes, postulaciones ni búsqueda de empleo. Empleo permanece Post-MVP.



\## Contexto arquitectónico



```mermaid

flowchart LR

&#x20;   G\[cameia-gateway] --> P\[cameia-perfil]

&#x20;   P --> DB\[(PostgreSQL Perfil)]

&#x20;   P -. eventos de perfil .-> R\[RabbitMQ]

&#x20;   P -. capacidades IA futuras .-> L\[Proveedor LLM]

&#x20;   P -. trazas previstas .-> LF\[Langfuse]

```



\## Tecnología prevista



| Elemento | Línea base |

|---|---|

| Lenguaje | Java 21 |

| Framework | Spring Boot 4.1.1 |

| Build | Maven; wrapper pendiente de confirmar |

| Persistencia | PostgreSQL 16, base/rol propios |

| Mensajería | RabbitMQ cuando existan contratos aprobados |

| Ejecución objetivo | Servicio HTTP y consumidor dentro del mismo repositorio/imagen |



\## Reglas de dominio relevantes



\- La identidad compartida es `firebaseUid`; no existe una entidad Usuario duplicada.

\- Todo dato generado o modificado con IA debe conservar procedencia y revisión humana.

\- Los contextos no comparten tablas ni claves foráneas.

\- Un archivo de CV no se conserva por defecto: la línea base plantea persistir texto procesado.



\## Ejecución local



```text

Instalación: pendiente de confirmar en CM-102

Pruebas: pendiente de confirmar en CM-102

Build: pendiente de confirmar en CM-102

Inicio: pendiente de confirmar en CM-102

Health check: pendiente de confirmar en CM-102

```



\## Configuración, seguridad y calidad



\- No guardar CV, PII, tokens, secretos ni `.env` en Git.

\- Usar datos sintéticos o anonimizados en pruebas.

\- Probar completitud, edición, versionamiento y autorización por propietario.

\- Verificar procedencia/revisión cuando se incorpore IA.

\- Activar CI únicamente con comandos comprobados por el responsable.



\## Contribución



\- `main` es estable y solo recibe promociones `develop → main` mediante Merge commit.

\- `develop` integra ramas `CM-<numero>-<descripcion-kebab-case>` mediante Squash.

\- Todo cambio ordinario entra mediante PR y revisión distinta del autor; la rama `CM-\*` se elimina después.



\## Cuándo actualizar este README



Actualizarlo en el mismo PR que cambie propósito, stack, comandos, variables, endpoints, modelo de perfil, eventos, IA, persistencia, pruebas, despliegue o responsables. Si no aplica, justificarlo en el PR.




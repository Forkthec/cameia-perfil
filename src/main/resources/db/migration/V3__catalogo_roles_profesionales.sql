-- CM-23: catálogo de roles profesionales TI + FK en rol_objetivo

-- 1. Tabla catálogo
CREATE TABLE rol_profesional (
    id        UUID         NOT NULL,
    nombre    VARCHAR(255) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    CONSTRAINT pk_rol_profesional PRIMARY KEY (id)
);

-- 2. Seed: catálogo genérico de roles TI (~45 roles)
INSERT INTO rol_profesional (id, nombre, categoria) VALUES
    -- Desarrollo
    ('a0000001-0000-0000-0000-000000000001', 'Desarrollador Frontend',              'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000002', 'Desarrollador Backend',               'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000003', 'Desarrollador Full Stack',            'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000004', 'Desarrollador Mobile Android',        'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000005', 'Desarrollador Mobile iOS',            'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000006', 'Desarrollador Mobile Multiplataforma','Desarrollo'),
    ('a0000001-0000-0000-0000-000000000007', 'Arquitecto de Software',              'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000008', 'Arquitecto de Soluciones',            'Desarrollo'),
    ('a0000001-0000-0000-0000-000000000009', 'Ingeniero de Software',               'Desarrollo'),
    -- Datos e IA
    ('a0000002-0000-0000-0000-000000000001', 'Científico de Datos',                        'Datos e IA'),
    ('a0000002-0000-0000-0000-000000000002', 'Analista de Datos',                          'Datos e IA'),
    ('a0000002-0000-0000-0000-000000000003', 'Ingeniero de Datos',                         'Datos e IA'),
    ('a0000002-0000-0000-0000-000000000004', 'Ingeniero de Machine Learning',              'Datos e IA'),
    ('a0000002-0000-0000-0000-000000000005', 'Analista de Inteligencia de Negocios',       'Datos e IA'),
    ('a0000002-0000-0000-0000-000000000006', 'Especialista en Visión por Computador',      'Datos e IA'),
    ('a0000002-0000-0000-0000-000000000007', 'Especialista en Procesamiento de Lenguaje Natural', 'Datos e IA'),
    -- Infraestructura
    ('a0000003-0000-0000-0000-000000000001', 'Ingeniero DevOps',                    'Infraestructura'),
    ('a0000003-0000-0000-0000-000000000002', 'Ingeniero Cloud',                     'Infraestructura'),
    ('a0000003-0000-0000-0000-000000000003', 'Ingeniero SRE',                       'Infraestructura'),
    ('a0000003-0000-0000-0000-000000000004', 'Administrador de Sistemas',           'Infraestructura'),
    ('a0000003-0000-0000-0000-000000000005', 'Administrador de Bases de Datos',     'Infraestructura'),
    ('a0000003-0000-0000-0000-000000000006', 'Ingeniero de Redes',                  'Infraestructura'),
    ('a0000003-0000-0000-0000-000000000007', 'Especialista en Seguridad Informática','Infraestructura'),
    ('a0000003-0000-0000-0000-000000000008', 'Analista de Ciberseguridad',          'Infraestructura'),
    -- Producto y Diseño
    ('a0000004-0000-0000-0000-000000000001', 'Diseñador UX/UI',                    'Producto y Diseño'),
    ('a0000004-0000-0000-0000-000000000002', 'Diseñador de Producto',              'Producto y Diseño'),
    ('a0000004-0000-0000-0000-000000000003', 'Gerente de Producto',                'Producto y Diseño'),
    ('a0000004-0000-0000-0000-000000000004', 'Product Owner',                      'Producto y Diseño'),
    ('a0000004-0000-0000-0000-000000000005', 'Investigador de UX',                 'Producto y Diseño'),
    -- Gestión
    ('a0000005-0000-0000-0000-000000000001', 'Gerente de Proyectos TI',            'Gestión'),
    ('a0000005-0000-0000-0000-000000000002', 'Scrum Master',                       'Gestión'),
    ('a0000005-0000-0000-0000-000000000003', 'Líder Técnico',                      'Gestión'),
    ('a0000005-0000-0000-0000-000000000004', 'Director de Tecnología',             'Gestión'),
    ('a0000005-0000-0000-0000-000000000005', 'Director de Sistemas',               'Gestión'),
    ('a0000005-0000-0000-0000-000000000006', 'Agile Coach',                        'Gestión'),
    -- Calidad
    ('a0000006-0000-0000-0000-000000000001', 'Ingeniero de Pruebas (QA)',                  'Calidad'),
    ('a0000006-0000-0000-0000-000000000002', 'Analista de Calidad de Software',            'Calidad'),
    ('a0000006-0000-0000-0000-000000000003', 'Ingeniero de Automatización de Pruebas',     'Calidad'),
    ('a0000006-0000-0000-0000-000000000004', 'Especialista en Pruebas de Rendimiento',     'Calidad'),
    -- Otros TI
    ('a0000007-0000-0000-0000-000000000001', 'Analista de Sistemas',               'Otros TI'),
    ('a0000007-0000-0000-0000-000000000002', 'Analista Funcional',                 'Otros TI'),
    ('a0000007-0000-0000-0000-000000000003', 'Consultor TI',                       'Otros TI'),
    ('a0000007-0000-0000-0000-000000000004', 'Especialista en ERP',                'Otros TI'),
    ('a0000007-0000-0000-0000-000000000005', 'Técnico de Soporte TI',              'Otros TI'),
    ('a0000007-0000-0000-0000-000000000006', 'Desarrollador Blockchain',           'Otros TI'),
    ('a0000007-0000-0000-0000-000000000007', 'Especialista en IoT',                'Otros TI');

-- 3. Columna nullable primero (filas existentes en rol_objetivo quedan en NULL)
ALTER TABLE rol_objetivo
    ADD COLUMN rol_profesional_id UUID;

-- 4. FK después de que el catálogo existe (filas con NULL no violan la FK)
ALTER TABLE rol_objetivo
    ADD CONSTRAINT fk_rol_objetivo_rol_profesional
        FOREIGN KEY (rol_profesional_id) REFERENCES rol_profesional(id);

-- CM-82: columnas de traducción al inglés para el catálogo de roles profesionales

ALTER TABLE rol_profesional
    ADD COLUMN nombre_en   VARCHAR(255),
    ADD COLUMN categoria_en VARCHAR(100);

UPDATE rol_profesional SET nombre_en = 'Frontend Developer',                    categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Backend Developer',                     categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'Full Stack Developer',                  categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'Android Mobile Developer',              categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000004';
UPDATE rol_profesional SET nombre_en = 'iOS Mobile Developer',                  categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000005';
UPDATE rol_profesional SET nombre_en = 'Cross-Platform Mobile Developer',       categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000006';
UPDATE rol_profesional SET nombre_en = 'Software Architect',                    categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000007';
UPDATE rol_profesional SET nombre_en = 'Solutions Architect',                   categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000008';
UPDATE rol_profesional SET nombre_en = 'Software Engineer',                     categoria_en = 'Development'        WHERE id = 'a0000001-0000-0000-0000-000000000009';

UPDATE rol_profesional SET nombre_en = 'Data Scientist',                        categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Data Analyst',                          categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'Data Engineer',                         categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'Machine Learning Engineer',             categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000004';
UPDATE rol_profesional SET nombre_en = 'Business Intelligence Analyst',         categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000005';
UPDATE rol_profesional SET nombre_en = 'Computer Vision Specialist',            categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000006';
UPDATE rol_profesional SET nombre_en = 'Natural Language Processing Specialist',categoria_en = 'Data & AI'          WHERE id = 'a0000002-0000-0000-0000-000000000007';

UPDATE rol_profesional SET nombre_en = 'DevOps Engineer',                       categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Cloud Engineer',                        categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'SRE Engineer',                          categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'Systems Administrator',                 categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000004';
UPDATE rol_profesional SET nombre_en = 'Database Administrator',                categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000005';
UPDATE rol_profesional SET nombre_en = 'Network Engineer',                      categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000006';
UPDATE rol_profesional SET nombre_en = 'Information Security Specialist',       categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000007';
UPDATE rol_profesional SET nombre_en = 'Cybersecurity Analyst',                 categoria_en = 'Infrastructure'     WHERE id = 'a0000003-0000-0000-0000-000000000008';

UPDATE rol_profesional SET nombre_en = 'UX/UI Designer',                        categoria_en = 'Product & Design'  WHERE id = 'a0000004-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Product Designer',                      categoria_en = 'Product & Design'  WHERE id = 'a0000004-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'Product Manager',                       categoria_en = 'Product & Design'  WHERE id = 'a0000004-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'Product Owner',                         categoria_en = 'Product & Design'  WHERE id = 'a0000004-0000-0000-0000-000000000004';
UPDATE rol_profesional SET nombre_en = 'UX Researcher',                         categoria_en = 'Product & Design'  WHERE id = 'a0000004-0000-0000-0000-000000000005';

UPDATE rol_profesional SET nombre_en = 'IT Project Manager',                    categoria_en = 'Management'        WHERE id = 'a0000005-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Scrum Master',                          categoria_en = 'Management'        WHERE id = 'a0000005-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'Tech Lead',                             categoria_en = 'Management'        WHERE id = 'a0000005-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'Chief Technology Officer',              categoria_en = 'Management'        WHERE id = 'a0000005-0000-0000-0000-000000000004';
UPDATE rol_profesional SET nombre_en = 'Chief Information Officer',             categoria_en = 'Management'        WHERE id = 'a0000005-0000-0000-0000-000000000005';
UPDATE rol_profesional SET nombre_en = 'Agile Coach',                           categoria_en = 'Management'        WHERE id = 'a0000005-0000-0000-0000-000000000006';

UPDATE rol_profesional SET nombre_en = 'QA Engineer',                           categoria_en = 'Quality'           WHERE id = 'a0000006-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Software Quality Analyst',              categoria_en = 'Quality'           WHERE id = 'a0000006-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'Test Automation Engineer',              categoria_en = 'Quality'           WHERE id = 'a0000006-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'Performance Testing Specialist',        categoria_en = 'Quality'           WHERE id = 'a0000006-0000-0000-0000-000000000004';

UPDATE rol_profesional SET nombre_en = 'Systems Analyst',                       categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000001';
UPDATE rol_profesional SET nombre_en = 'Functional Analyst',                    categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000002';
UPDATE rol_profesional SET nombre_en = 'IT Consultant',                         categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000003';
UPDATE rol_profesional SET nombre_en = 'ERP Specialist',                        categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000004';
UPDATE rol_profesional SET nombre_en = 'IT Support Technician',                 categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000005';
UPDATE rol_profesional SET nombre_en = 'Blockchain Developer',                  categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000006';
UPDATE rol_profesional SET nombre_en = 'IoT Specialist',                        categoria_en = 'Other IT'          WHERE id = 'a0000007-0000-0000-0000-000000000007';

-- Tras poblar, hacer las columnas NOT NULL
ALTER TABLE rol_profesional
    ALTER COLUMN nombre_en    SET NOT NULL,
    ALTER COLUMN categoria_en SET NOT NULL;

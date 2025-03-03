# Instrucciones para la Documentación

Hemos añadido tres formas sencillas de generar y visualizar la documentación del proyecto con Dokka.

## Opción 1: Servidor integrado de Java (RECOMENDADO)

Este método utiliza un servidor HTTP integrado de Java, sin dependencias externas:

```
.\gradlew :app:serveDocs
```

Este comando:
1. Genera la documentación HTML con Dokka
2. Inicia un servidor HTTP integrado en el puerto 8090
3. Abre automáticamente tu navegador en http://localhost:8090

Para detener el servidor, presiona `Ctrl+C` en la terminal.

## Opción 2: Servidor Python

Este método utiliza Python para iniciar un servidor web local:

```
.\gradlew :app:serveDocumentation
```

Este comando:
1. Genera la documentación HTML con Dokka
2. Inicia un servidor web Python en el puerto 8080
3. Abre automáticamente tu navegador en http://localhost:8080

Para detener el servidor, presiona `Ctrl+C` en la terminal.

## Opción 3: Abrir directamente en el navegador

Si prefieres simplemente abrir la documentación en tu navegador sin iniciar un servidor:

```
.\gradlew :app:viewDocumentation
```

Este comando:
1. Genera la documentación HTML con Dokka
2. Abre el archivo index.html directamente en tu navegador predeterminado

## Requisitos

- **Opción 1 (serveDocs)**: No requiere software adicional, usa Java incluido en el proyecto
- **Opción 2 (serveDocumentation)**: Python debe estar instalado en tu sistema
- **Opción 3 (viewDocumentation)**: No tiene requisitos adicionales

## Ventajas del servidor web

Utilizar un servidor web local (opciones 1 o 2) ofrece estas ventajas:

1. Las referencias entre páginas funcionan mejor
2. Los scripts de navegación se ejecutan correctamente
3. No hay restricciones de seguridad del navegador para archivos locales
4. Puedes compartir la documentación con otros desarrolladores en tu red local

## Solución de problemas

Si encuentras problemas:

1. **Error de puertos**: Si el puerto está en uso, edita `app/build.gradle.kts` y cambia el número de puerto
2. **Navegador no abre**: Accede manualmente a http://localhost:8090 (opción 1) o http://localhost:8080 (opción 2)
3. **Python no disponible**: Usa la opción 1 (serveDocs) que no requiere Python
4. **Scripts de navegación no funcionan**: Prueba la solución descrita en LEEME.md o usa un servidor web 
# Generación de Diagramas UML para la Aplicación Perros

## Introducción

Hemos agregado la capacidad de generar diagramas UML para visualizar la estructura de clases del proyecto. Estos diagramas son útiles para entender la arquitectura de la aplicación y las relaciones entre sus componentes.

## Comandos Disponibles

Para generar los diagramas UML, puedes usar los siguientes comandos:

```bash
# Genera ambos tipos de diagramas (manual y automático)
.\gradlew generarTodosUML

# Genera solo el diagrama manual con más detalles
.\gradlew generarUML

# Genera solo el diagrama automático basado en análisis de código
.\gradlew analizarClases
```

## Tipos de Diagramas

### 1. Diagrama Manual (`generarUML`)

Este diagrama está diseñado manualmente para mostrar las relaciones más importantes entre clases, con métodos y propiedades seleccionados específicamente para mayor claridad.

**Ventajas:**
- Estructura limpia y fácil de entender
- Relaciones claras y bien definidas
- Incluye solo los métodos y propiedades más relevantes

### 2. Diagrama por Análisis de Código (`analizarClases`)

Este diagrama se genera automáticamente analizando el código fuente del proyecto.

**Ventajas:**
- Se actualiza automáticamente cuando cambia el código
- Incluye todas las clases detectadas
- Muestra las relaciones reales encontradas en el código

## Visualización de los Diagramas

Los diagramas se generan en formato PlantUML (`.puml`) en el directorio `app/build/uml/`. Para visualizarlos tienes varias opciones:

1. **Utilizar los archivos HTML generados:**
   - Abre `app/build/uml/ver-diagrama.html` para el diagrama manual
   - Abre `app/build/uml/ver-diagrama-analizado.html` para el diagrama generado automáticamente

2. **Usar PlantText (online):**
   - Ve a [PlantText](https://www.planttext.com/)
   - Copia el contenido del archivo `.puml` correspondiente
   - Pégalo en el editor y haz clic en "Refresh" para ver el diagrama

3. **Usar VSCode con extensión PlantUML:**
   - Instala la [extensión PlantUML](https://marketplace.visualstudio.com/items?itemName=jebbs.plantuml)
   - Abre el archivo `.puml` correspondiente
   - Presiona Alt+D para visualizarlo

## Personalización

Si deseas modificar el diagrama manual:

1. Edita la función `generarUML` en `app/build.gradle.kts`
2. Modifica la variable `diagramaContent` con la estructura de clases que desees
3. Ejecuta nuevamente `.\gradlew generarUML` para regenerar el diagrama

## Requisitos

- **Visualización:** No se requiere software adicional si usas los archivos HTML o PlantText online
- **Extensión VSCode:** Si prefieres usar VSCode, necesitarás la extensión PlantUML

## Problemas Comunes

Si encuentras algún problema:

- **Diagrama no visible:** Asegúrate de usar el servicio online o tener instalado Graphviz si usas la extensión VSCode
- **Error al generar:** Verifica que los archivos Kotlin existan en `src/main/java/com/example/perros`
- **Diagrama incompleto:** El análisis automático puede no detectar todas las relaciones implícitas

Para más información sobre la sintaxis de PlantUML, consulta la [documentación oficial](https://plantuml.com/class-diagram). 
package com.example.perros

/**
 * Clase que representa un perro en la aplicación.
 *
 * Esta clase se utiliza para manejar la información básica de un perro en la aplicación.
 * Almacena datos esenciales que se utilizan tanto en la interfaz de usuario como
 * en la base de datos de Firebase.
 *
 * @property id Identificador único del perro en la base de datos de Firebase
 * @property nombre Nombre del perro que se muestra en la interfaz
 * @property imageBase64 Imagen del perro codificada en Base64. Puede ser null si el perro no tiene imagen
 *                      La imagen se utiliza tanto en el spinner como en el marcador del mapa
 * 
 * @see MapsActivity donde se utiliza para mostrar los marcadores en el mapa
 * @see DogSpinnerAdapter donde se utiliza para mostrar la lista de perros
 */
data class DogItem(
    val id: String,
    val nombre: String,
    val imageBase64: String?
)

# **Google Drive: diseño**

Prototipo del capítulo 15 de *System Design Interview* (Alex Xu). Este documento explica qué construí, por qué de esta forma, qué dejé afuera y cómo usé la IA. Cómo correrlo, los endpoints y las reglas de la API están en el [README](https://claude.ai/README.md).

## **1\. Alcance respecto del libro**

**Implementado.** Las tres operaciones de la API del capítulo (subir, descargar y listar las revisiones de un archivo), más listar, renombrar y borrar. También la estrategia del libro para los conflictos: cuando dos cambios chocan, gana el primero que se procesa.

**Cómo se relaciona mi esquema con el del libro:Fuera de alcance, a propósito:**

* ***`user` → `Persona`:***   
  * Mientras que el diseño original plantea una cuenta completa integrada a un sistema de login, en mi implementación **opté por simplificar** la entidad para que almacene únicamente nombre, apellido y correo único, sin contraseñas ni manejo de sesiones. Para simular el contexto de ejecución, la API identifica quién opera enviando su ID en el header `X-User-Id`.   
  * **Por qué:** Prioricé resolver el dominio central de Drive sin sumar la sobrecarga de implementar autenticación real. Es el principal *trade-off* del   
* ***file → Archivo:***  
  * En este caso decidí mantener la estrategia del diseño original: Archivo representa el recurso tal como lo ve el usuario (dueño, nombre, tipo y fechas) y, además, desnormaliza los metadatos de la revisión vigente (ubicación de los bytes, checksum, tamaño y formato) para no tener que consultar el historial.  
  * Por qué: Listar archivos y descargar la versión actual son las operaciones más frecuentes del sistema, y esta estructura evita hacer joins o buscar siempre la última revisión. El trade-off es la duplicación de datos entre Archivo y su revisión activa; para garantizar la consistencia, restringí su actualización únicamente a través de agregarRevision, evitando puntos de modificación dispersos que puedan desincronizar el estado.  
* ***file\_version → RevisionArchivo:***  
  * Al igual que en el modelo del libro, diseñé el historial como una estructura de solo escritura (append-only): cada RevisionArchivo registra su número, referencia a los bytes, checksum y fecha, y nunca se edita; subir contenido nuevo siempre inserta una fila nueva.  
  * Por qué: La inmutabilidad es lo que vuelve confiable al historial: si una revisión pudiera alterarse, dejaría de garantizarse que "la revisión 2" sea exactamente lo que se subió en ese momento.

* ***block → un storageKey por revisión:***  
  * Mientras que el modelo de referencia fragmenta cada archivo en múltiples bloques para transferir únicamente las partes modificadas, en mi implementación **opté por almacenar** cada revisión como un único archivo íntegro en disco, referenciado mediante un storageKey.  
  * **Por qué:** mplementar *chunking*, control de integridad por fragmento y reensamblado era un desarrollo muy largo al que no llegaba con los tiempos del proyecto.  Para acotar el alcance de forma segura sin esa complejidad, **fijé un límite máximo de 20 MB por archivo**; además, como la API recibe el archivo completo en cada *request* (sin un cliente que calcule *diffs*), partirlo en bloques tampoco habría generado un ahorro real.   
* ***device y namespace → fuera de alcance:***  
  * En el diseño original, device registra los distintos dispositivos de un usuario para notificar cambios y mantenerlos sincronizados, mientras que namespace modela su espacio raíz para organizar jerarquías de carpetas. En mi prototipo **decidí dejar ambas entidades fuera de alcance**.  
  * **Por qué:** Al no haber clientes suscriptos a eventos de sincronización en tiempo real, llevar un registro de dispositivos no tiene ningún consumidor dentro del sistema. Por otro lado, **no llegué a implementar la jerarquía de carpetas** en esta entrega y acoté el modelo a una lista plana de archivos (una funcionalidad que definitivamente incluiría en una próxima iteración); sin carpetas que organizar, abstraer un namespace resultaba innecesario, ya que la pertenencia de cada recurso queda resuelta de forma directa mediante duenioId. 


| Del libro o de un Drive real | Por qué quedó afuera | Cómo lo haría |
| ----- | ----- | ----- |
| Bloques y sincronización por diferencias | Solo valen con un cliente que sincroniza; acá el cliente es Postman | Partir el archivo en bloques con su hash y subir solo los que cambiaron |
| Deduplicación | Complejiza el borrado físico de archivos, ya que exige verificar que ningún otro recurso siga apuntando a los mismos bytes  | Aprovechar el *checksum* que ya calcula la API para no duplicar archivos idénticos en disco, gestionando la eliminación mediante conteo de referencias (*reference counting*).  |
| Notificaciones (long polling) | Sin clientes que sincronizan, no hay a quién avisar | Agregar un servicio de notificaciones desacoplado que avise a los dispositivos activos del usuario ante cada modificación.  |
| Subida reanudable | Con el tope de 20 MB por archivo, las transferencias son cortas y no justifican mantener estados de subida intermedios. | Implementar envíos por partes asociados a un ID de sesión para retomar transferencias interrumpidas sin reiniciar desde cero.  |
| Compartir con permisos | Implica construir un modelo de autorización completo (roles y validación en cada *endpoint*).  | Crear una entidad intermedia que relacione Archivo, Persona y tipo de permiso (LECTURA o ESCRITURA), validándolo antes de cada operación.  |
| Carpetas | No me alcanzaba el tiempo para hacerlas bien (ver "Próximos pasos") | — |
| Rate limiting | No formaba parte del capítulo de referencia, aunque es indispensable para proteger la API. | Un límite de pedidos por usuario, delante de la API |

## **2\. Arquitectura: metadata separada del contenido**

```
Controller ──> Service ──┬──> Repository (JPA) ──> MySQL   metadata: nombre, dueño, fechas, revisiones
                         └──> IBlobStorage ──────> disco   contenido: los bytes, por storageKey
```

* **Controller:** recibe el pedido HTTP, llama al servicio y arma la respuesta. No tiene lógica.  
* **Service:** coordina la operación (validar, guardar bytes, guardar metadata) y decide qué hacer si algo falla.  
* **Dominio** (Archivo, RevisionArchivo): las reglas propias del archivo, sin depender de Spring.  
* **IBlobStorage:** guarda y lee bytes por clave. Hoy, LocalBlobStorage los guarda en disco; en producción sería S3. El servicio depende solo de la interfaz, así que cambiar de disco a S3 no toca ArchivoService. Es una interfaz chica que esconde todo el detalle del almacenamiento (un "módulo profundo", en términos de APOSD).

## **3\. El modelo de revisiones**

* **RevisionArchivo es inmutable.** Subir contenido nuevo crea una revisión; la anterior no se modifica y se puede seguir descargando. Solo Archivo.agregarRevision crea revisiones y decide su número.  
* **Archivo guarda una copia de la revisión actual** (storageKey, checksum, tamanio, contentType). Es un trade-off: las lecturas comunes (listar, descargar el contenido actual) no necesitan cargar el historial de revisiones, a cambio de mantener los datos sincronizados. Por eso solo agregarRevision los modifica. La tabla file del libro hace lo mismo.  
* **Una revisión tiene que tener el mismo formato que el archivo.** Si cv.pdf recibiera una revisión PNG, el nombre, el tipo y el contenido dejarían de coincidir. Se rechaza con 400\.  
* **Renombrar no crea una revisión:** cambia el archivo, no su contenido.

## **4\. Concurrencia: gana el primero**

Hay dos números con propósitos distintos:

* **version** (@Version de JPA) aumenta con **cualquier** cambio: renombrar o subir una revisión.  
* **revisionActual** aumenta **solo** cuando cambia el contenido. Es el número del historial.

**Por qué hacen falta los dos.** Ana tiene foto.png abierto en la compu y en el celular: los dos leen version 0 y revisión 1\. Desde el celular lo renombra a playa.png, y la version pasa a 1, pero la revisión sigue en 1, porque el contenido no cambió. Desde la compu, con la información vieja, sube contenido nuevo mandando version 0, y recibe 409\. Si el control usara el número de revisión, no habría detectado el cambio de nombre y la compu habría subido sin enterarse.

**Cómo funciona:**

* El cliente manda la version que leyó. Si no coincide con la actual, el servicio responde 409 sin escribir nada. Esto cubre al cliente que trabaja con datos viejos.  
* Si dos pedidos pasan esa verificación a la vez, el @Version hace que el segundo guardado falle. Es el "gana el primero" del libro.

**Alternativa que descarté:** que gane el último cambio. Para un nombre sería aceptable, pero para el contenido, un cambio pisado sin aviso es una pérdida real.

### **Nombres únicos**

Dos subidas simultáneas pueden elegir el mismo nombre. Al subir, un nombre repetido se numera (foto (1).png); al renombrar, se rechaza, porque el usuario lo eligió a propósito. Consideré tomar la última copia y sumarle 1, pero el orden alfabético no es numérico ((10) va antes que (2)) y la fecha no refleja el número: probar candidatos en orden es simple y siempre correcto.

## **5\. Consistencia entre la base y el disco**

Como la base de datos y el sistema de archivos no comparten una transacción atómica, **definí un orden de ejecución** para cada operación con el objetivo de evitar estados corruptos si algo falla a mitad de camino: 

| Operación | Orden | Si falla a la mitad |
| ----- | ----- | ----- |
| Subir archivo  | 1\. Escribir bytes en disco 2\. Guardar metadata en la base | Se borran los bytes recién escritos |
| Subir revisión | Igual que subir | Se eliminan los bytes nuevos y la revisión vigente no se altera.  |
| Borrar | 1\. Eliminar metadata en la base 2\. Borrar los bytes de todas sus revisiones  | Si falla el disco, quedan bytes huérfanos que se registran en los logs para limpieza posterior. |
| Copia cortada | — | LocalBlobStorage borra el archivo parcial |

**Por qué elegí este orden:** En el peor escenario posible, un fallo deja **bytes huérfanos en disco**; esto es invisible para el usuario, no afecta la integridad funcional y puede limpiarse de forma diferida a partir de los logs. Invertir el orden implicaría el riesgo opuesto: dejar metadata apuntando a archivos inexistentes en disco, lo que rompería el listado y provocaría errores al intentar descargar. 

**Por qué `subir` no tiene `@Transactional` y `subirRevision` sí:**

* ***subir** (sin @Transactional): Al crear una entidad nueva, llamar a save() sin una transacción envolvente hace que el commit ocurra de forma inmediata dentro del bloque try. Si surge un error en la base, la excepción se captura en ese mismo instante y el catch ejecuta la compensación borrando los bytes recién escritos en disco. Si hubiera usado @Transactional, el flush y el commit se postergarían hasta el cierre del método (fuera del try), impidiendo capturar fallos de confirmación para limpiar el disco.*  
* ***subirRevision** (con @Transactional \+ saveAndFlush): A diferencia de una alta nueva, esta operación modifica un Archivo existente y accede a su colección de revisiones cargada de forma diferida (lazy loading), por lo que requiere mantener abierta la sesión de persistencia durante todo el método. Como @Transactional retrasa la confirmación hasta el final, forcé la sincronización mediante saveAndFlush() dentro del try: de esta manera, cualquier violación de unicidad por subidas concurrentes dispara la excepción en ese punto y permite eliminar los bytes nuevos antes de salir del método.*  
* ***borrar** (sin @Transactional): Al omitir la anotación, la eliminación de la metadata se confirma en la base de datos antes de avanzar hacia el borrado físico.* Esto asegura que el registro deje de existir para los usuarios antes de tocar los archivos en disco.


## **6\. Checksum**

Calculo el SHA-256 mientras se copian los bytes del request al disco (usando DigestInputStream), evitando tener que volver a leer el archivo después de guardarlo para calcular su hash. Se guarda en cada revisión y se devuelve como ETag al descargar. Es la base que haría falta para deduplicar, como propone el libro. Trade-off: no se verifica al descargar, así que un archivo dañado en disco no se detectaría.

## **7\. Decisiones de la API**

* **Las revisiones son un subrecurso del archivo** (/api/archivos/{id}/revisiones), porque no existen sin él.  
* POST /revisiones y no PUT /contenido: subir contenido nuevo crea una revisión, no reemplaza la anterior. El Location de la respuesta apunta al contenido de la revisión creada.  
* **Metadata y contenido separados:** GET /{id} devuelve JSON y GET /{id}/contenido, los bytes.  
* **El historial devuelve 20 revisiones por defecto,** permitiendo solicitar entre **1 y 100**.  
* **El storageKey nunca se expone:** es un detalle interno del almacenamiento.

## **8\. Seguridad**

* **X-User-Id es una simplificación, no autenticación:** cualquiera podría mandar el id de otra persona. En producción, el id saldría de un token validado (por ejemplo, JWT).  
* **Un archivo ajeno responde 404, no 403,** para no confirmar que existe. Todas las búsquedas filtran por id **y** dueño (evita IDOR), y los ids son UUID, no secuenciales.  
* **Lista blanca de formatos,** sin HTML ni SVG: como el contenido se muestra en el navegador, podrían ejecutar scripts en el dominio de la app (XSS almacenado). Se suma el header X-Content-Type-Options: nosniff, que obliga al navegador a respetar el Content-Type declarado en vez de adivinarlo.  
* **El nombre en disco es un UUID,** nunca el que manda el usuario: evita el *path traversal* (un nombre como `../../x`).  
* **Los errores 500 no exponen detalles internos** (tablas, SQL). El detalle queda en el log.  
* **Dependencias:** fijé la versión de Tomcat embebido por CVE conocidos, con un comentario en el pom.xml que explica cuándo sacarlo.  
* **Limitación conocida:** el formato se toma del Content-Type que declara el cliente, sin verificar los bytes reales. El paso siguiente sería detectarlo por los primeros bytes del archivo (por ejemplo, con Apache Tika).

## **9\. Tests**

| Nivel | Clases | Qué prueban |
| ----- | ----- | ----- |
| Dominio | ArchivoTest, TipoArchivoTest | Validaciones, revisiones y lista blanca, sin Spring |
| Servicio | ArchivoServiceTest | Con mocks y reloj fijo: orden de escritura y borrado, limpieza de bytes, conflictos, acceso de otro usuario |
| Storage | LocalBlobStorageTest | Guardar, leer y borrar con archivos reales en una carpeta temporal; checksum |
| Web | ArchivoControllerTest | Códigos HTTP, headers (ETag, Location) y que no se exponga el storageKey |
| Integración | ArchivoIntegracionTest | El flujo completo contra MySQL real y disco real |

* **Los tests de integración corren contra MySQL real** (Testcontainers) y no contra una base en memoria, para probar la misma base que usa la aplicación. Si Docker no está disponible, se saltean y el resto corre igual.  
* **Los métodos sin lógica propia** (como listar, que solo delega en una consulta) no tienen tests unitarios: con mocks solo probarían el mock. Su consulta se prueba en integración.  
* **Stack traces esperados:** al correr los tests, el log muestra errores como "detalle interno" o "disco no disponible". Son de tests que simulan fallas a propósito y verifican que se logueen; esos tests pasan.  
* **Sin test automatizado:** el borrado del archivo parcial cuando una copia se corta, porque requiere simular un corte de conexión a mitad de la copia.

## **10\. Limitaciones y próximos pasos**

**Simplificaciones conscientes:**

* **Paginación en memoria al listar revisiones:** Aunque el endpoint limita la respuesta (entre 1 y 100 elementos), actualmente consulta todo el historial y recorta la página en memoria. Para un prototipo con pocos cambios por archivo es suficiente, pero para escalar trasladaría el paginado directamente a la consulta SQL   
* **Listado de archivos sin paginar:** De forma similar, listarArchivos devuelve todos los recursos del usuario en una única respuesta para simplificar el consumo inicial. A medida que crezca el volumen de archivos por cuenta, incorporaría paginación en el endpoint.   
* **Retención ilimitada de revisiones:** No establecí un tope máximo de versiones por archivo. Implementar una política de retención (por ejemplo, conservar solo las últimas $N$) requiere coordinar el borrado con cuidado: al igual que al eliminar un archivo, primero habría que confirmar la baja en la base de datos y recién después borrar los bytes de las revisiones descartadas en disco para no dejar referencias rotas.   
* Un SELECT extra antes de cada INSERT: Como asigno el id en el constructor de la entidad y el campo @Version es un tipo primitivo (long), Spring Data JPA no puede inferir si la entidad es nueva y ejecuta merge() en lugar de persist(), disparando una consulta previa innecesaria.   
* **El storage local funciona en una sola instancia;** con varias, haría falta S3 o un almacenamiento compartido.

#### **Próximos pasos (con más tiempo de desarrollo)**

* **Autenticación real con JWT:** Reemplazar la identificación vía header `X-User-Id` por un flujo completo de login y validación de tokens JWT en Spring Security, extrayendo la identidad directamente del contexto de seguridad.  
* **Almacenamiento compatible con S3:** Implementar el contrato de almacenamiento contra una API S3 (incorporando un contenedor de **MinIO** en el `docker-compose` para mantener el entorno local reproducible), habilitando el escalado horizontal de la API.  
* **Detección real de tipo MIME por contenido:** Inspeccionar la cabecera de bytes del archivo (*magic numbers*, por ejemplo con Apache Tika) al momento de la subida, en lugar de confiar únicamente en el `Content-Type` declarado por el cliente.  
* **Limpieza periódica de bytes huérfanos:** Agregar una tarea programada (*scheduled job*) que cruce los archivos almacenados en disco contra las revisiones vigentes en la base de datos y elimine de forma segura los residuos que no tengan referencia.  
* **Jerarquía de carpetas:**  
  * **Modelo de datos:** Incorporar una entidad Carpeta autorreferenciada mediante padreId y vincular cada Archivo a través de carpetaId.  
  * **Unicidad sin depender de NULL:** Redefinir la restricción de nombre único a nivel (duenioId, carpetaId, nombre) y crear una carpeta raíz explícita para cada usuario. De esta forma se evita usar carpetaId \= NULL para la raíz, ya que MySQL no trata dos NULL como iguales en un índice único y permitiría archivos con el mismo nombre en el nivel principal.  
  * **Reglas de negocio:** Verificar en cada operación que la carpeta pertenezca al usuario autenticado, implementar borrado en cascada/recursivo sobre subcarpetas y archivos contenidos, y validar la cadena de ancestros al mover una carpeta para **impedir ciclos** (evitando que una carpeta pueda moverse dentro de sí misma o de un descendiente).

## **11\. Uso de IA**

Utilicé Claude a lo largo del proyecto como asistente de programación: para contrastar alternativas de diseño, acelerar la escritura de código y borradores de tests, revisar implementaciones y profundizar en conceptos puntuales. Sin embargo, **el diseño de cada caso de uso y la lógica de negocio estuvieron siempre bajo mi control**: yo definía qué métodos existían, cuál era su flujo de ejecución paso a paso, en qué orden ocurrían las operaciones (por ejemplo, la coordinación entre disco y base de datos) y qué debía devolver cada uno, delegando en la herramienta la traducción de esas especificaciones a código Java.

Todo el código generado fue verificado ejecutando la suite de tests y probando cada *endpoint* manualmente desde Postman. Además, siguiendo la consigna, dejé comentadas en el código aquellas partes o mecanismos internos que me resultaban menos familiares para asegurar su comprensión.
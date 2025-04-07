# Description
The semantification component is a data ingestion pipeline built with Apache NiFi for UC2. 

Each processed YOLO result is converted to an individual JSON-LD format and sent
to the Apache NiFi data pipeline running on the GLACIATION platform. The pipeline
includes decoding and storing images, as well as storing YOLO results in the DKG via
the metadata service. An example of such a JSON-LD file can be found in our use
case GitHub repository.
.


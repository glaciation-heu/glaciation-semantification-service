# Description
The semantification component is a data ingestion pipeline built with [Apache NiFi](https://archive.apache.org/dist/nifi/1.12.1/) for UC2. 
* URL: http://semantification.integration/nifi/ or *validation* instead of *integration* for the one deployed in internal validation cluster

Each processed YOLO result is converted to an individual JSON-LD format and sent
to the Apache NiFi data pipeline running on the GLACIATION platform. The pipeline
includes decoding and storing images, as well as storing YOLO results in the DKG via
the metadata service. An example of such a JSON-LD file can be found in our [use
case GitHub repository](https://github.com/glaciation-heu/DELL-UC).
.

![image](https://github.com/user-attachments/assets/70eed011-78da-4e90-aaeb-88a1c2962027)

Based on these use case datasets stored in DKG, we can run relevant workloads using
the data. One example is inspecting object distribution to understand the frequency
and distribution of detected objects across different robots on the floor. This in turn can
provide several key insights:
* Identifying commonly detected objects (e.g., tools, machines, vehicles) to track
the availability and utilization of assets.
* Detecting potential misplacement of items (e.g., safety equipment like helmets
or gloves in the wrong areas).

More implementation details regarding Use Case 2 can be found in our [use case
GitHub repository](https://github.com/glaciation-heu/DELL-UC), and [workload repository](https://github.com/glaciation-heu/glaciation-uc2-workload-service).



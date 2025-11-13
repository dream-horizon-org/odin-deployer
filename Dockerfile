FROM eclipse-temurin:17.0.15_6-jre-ubi9-minimal

# Install required tools
RUN microdnf install -y wget unzip tar && microdnf clean all

# Install Liquibase CLI 5.0.1
RUN wget -q https://github.com/liquibase/liquibase/releases/download/v5.0.1/liquibase-5.0.1.zip -O /tmp/liquibase.zip \
    && unzip /tmp/liquibase.zip -d /opt/liquibase \
    && ln -s /opt/liquibase/liquibase /usr/local/bin/liquibase \
    && rm /tmp/liquibase.zip

# Add MySQL Connector/J 9.4.0 for migrations
RUN wget -q https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar \
    -O /opt/liquibase/mysql-connector-java.jar

# Clean up tools not needed at runtime
RUN microdnf remove -y wget unzip tar && microdnf clean all

COPY target/odin-deployer /opt/odin-deployer/
COPY entrypoint.sh /opt/odin-deployer/entrypoint.sh
WORKDIR /opt/odin-deployer
ENTRYPOINT ["./entrypoint.sh"]

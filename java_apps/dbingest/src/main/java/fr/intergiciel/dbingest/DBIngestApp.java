package fr.intergiciel.dbingest;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class DBIngestApp {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker:29092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("topic1"));

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());
                // Informations de connexion à la base de données PostgreSQL
                String url = "jdbc:postgresql://mcdb:5432/mirthdb";
                String user = "mirthdb";
                String password = "mirthdb";

                // Données JSON à insérer
                String jsonData = record.value();

                try (Connection connection = DriverManager.getConnection(url, user, password)) {
                    // Extraire les données JSON
                    JSONObject data = new JSONObject(jsonData);
                    JSONObject patientData = data.getJSONObject("Patient");
                    JSONArray addressesData = data.getJSONArray("Addresses");
                    JSONObject movementsData = data.getJSONObject("Movements");
                    JSONObject stayData = data.getJSONObject("Stay");

                    // Insérer les données dans les tables correspondantes
                    insertPatient(connection, patientData);
                    for (int i = 0; i < addressesData.length(); i++) {
                        JSONObject addressData = addressesData.getJSONObject(i);
                        insertAddress(connection, patientData.getString("PatientID"), addressData);
                    }
                    insertStay(connection, patientData.getString("PatientID"), stayData);
                    insertMovement(connection, patientData.getString("PatientID"), stayData.getString("NumUniqueSejour"),movementsData);

                    System.out.println("Données insérées avec succès !");
                } catch (SQLException | JSONException | ParseException e) {
                    e.printStackTrace();
                }
            }
            consumer.commitSync(); // Confirmer les offsets après avoir traité tous les enregistrements
        }
    }

    private static void insertPatient(Connection connection, JSONObject patientData) throws SQLException, JSONException {
        String query = "INSERT INTO Patient (patient_id, birth_name, legal_name, first_name, prefix, birth_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, patientData.getString("PatientID"));
            statement.setString(2, patientData.getString("PatientBirthName"));
            statement.setString(3, patientData.getString("PatientLegalName"));
            statement.setString(4, patientData.getString("PatientFirstName"));
            statement.setString(5, patientData.getString("PatientPrefix"));

            // Convertir la date de naissance au format "12-10-1942" en java.sql.Date
            String birthDateStr = patientData.getString("PatientBirthDate");
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
            java.util.Date birthDateUtil = sdf.parse(birthDateStr);
            java.sql.Date birthDateSql = new java.sql.Date(birthDateUtil.getTime());

            statement.setDate(6, birthDateSql);
            statement.executeUpdate();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private static void insertAddress(Connection connection, String patientId, JSONObject addressData) throws SQLException, JSONException {
        String query = "INSERT INTO Address (address_index, street, other_street, city, state, postal_code, country, address_type, patient_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, addressData.getInt("addressIndex"));
            statement.setString(2, addressData.getString("StreetAddress"));
            statement.setString(3, addressData.getString("OtherDesignation"));
            statement.setString(4, addressData.getString("City"));
            statement.setString(5, addressData.getString("StateOrProvince"));
            statement.setString(6, addressData.getString("ZipOrPostalCode"));
            statement.setString(7, addressData.getString("Country"));
            statement.setString(8, addressData.getString("AddressType"));
            statement.setString(9, patientId);
            statement.executeUpdate();
        }
    }

    private static void insertMovement(Connection connection, String patientId, String numsej, JSONObject movementsData) throws SQLException, JSONException {
        String query = "INSERT INTO Movement (service, room, bed, num_sej, patient_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, movementsData.getString("Service"));
            statement.setString(2, movementsData.getString("Room"));
            statement.setString(3, movementsData.getString("Bed"));
            statement.setString(4, numsej);
            statement.setString(5, patientId);
            statement.executeUpdate();
        }
    }

    private static void insertStay(Connection connection, String patientId, JSONObject stayData) throws SQLException, JSONException, ParseException {
        String query = "INSERT INTO Stay (num_sej, start_date, end_date, patient_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, stayData.getString("NumUniqueSejour"));
            statement.setDate(2, parseDate(stayData.getString("AdmitDate")));
            statement.setDate(3, parseDate(stayData.getString("DischargeDate")));
            statement.setString(4, patientId);
            statement.executeUpdate();
        }
    }

    private static java.sql.Date parseDate(String dateString) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        java.util.Date parsed = format.parse(dateString);
        return new java.sql.Date(parsed.getTime());
    }
}






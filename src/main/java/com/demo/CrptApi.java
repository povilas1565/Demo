package com.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class CrptApi {
    public Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private static final String URL = "https://ismp.crpt.ru/api/v3";
    private static final String CREATE = "/lk/documents/create";

    private final Limit limit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive");
        }
        this.limit = new Limit(timeUnit, requestLimit);
    }


    public String createDocumentRF(Document doc, String signature) {
        Converter converterJson = new ConverterJson();
        String docJson = encodeBase64(convert(doc, converterJson));
        Body body = new Body(Document_Format.MANUAL, docJson, Type.LP_INTRODUCE_GOODS, signature);
        String bodyJson = convert(body, converterJson);

        return requestPost(URL.concat(CREATE), bodyJson, ContentType.APPLICATION_JSON);
    }

    private String encodeBase64(String data) {
        return new String(Base64.getEncoder().encode(data.getBytes()));
    }

    private String convert(Object body, Converter converter) {
        return converter.convert(body);
    }

    private String requestPost(String url, String bodyString, ContentType type) {

        Content postResult = null;
        try {
            if (limit.getLimit() >= 0) {
                postResult = Request.Post(url)
                        .bodyString(bodyString, type)
                        .execute().returnContent();
            } else {
                log.info("the request limit has ended in this time interval");
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return postResult != null ? postResult.asString() : "";
    }

    public Document newDocument(String doc_id, String doc_status,
                                String doc_type, boolean importRequest, String participant_inn,
                                String producer_inn, String production_date, String production_type,
                                List<Product> products, String reg_date, String reg_number) {

        Description description = this.new Description(participant_inn);

        return this.new Document(description, doc_id, doc_status, doc_type, importRequest, participant_inn, producer_inn, production_date, production_type, products, reg_date, reg_number);
    }

    private class Limit {
        private final TimeUnit timeUnit;
        private final int requestLimit;

        private AtomicInteger limit;
        private AtomicBoolean checkTime;

        public Limit(TimeUnit timeUnit, int requestLimit) {
            this.timeUnit = timeUnit;
            this.requestLimit = requestLimit;
            this.limit = new AtomicInteger(requestLimit);
            this.checkTime = new AtomicBoolean(true);
        }

        int getLimit() {
            if (checkTime.compareAndSet(true, false)) {
                limit = new AtomicInteger(requestLimit);
                new Thread(this::startTimeLimit).start();
            }
            return limit.decrementAndGet();
        }

        private void startTimeLimit() {
            try {
                Thread.sleep(timeUnit.toMillis(1));
                checkTime.set(true);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class Document {

        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class Product {
        String certificate_document;
        String certificate_document_date;
        String certificate_document_number;
        String owner_inn;
        String producer_inn;
        String production_date;
        String tnved_code;
        String uit_code;
        String uitu_code;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public class Description {
        private String participantInn;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    class Body {

        private Document_Format document_format;
        private String product_document;
        private Type type;
        private String signature;


    }

    interface Converter {
        String convert(Object body);
    }

    class ConverterJson implements Converter {
        private ObjectMapper mapper = new ObjectMapper();

        @Override
        public String convert(Object body) {
            String json = null;
            try {
                json = mapper.writeValueAsString(body);
                return json;
            } catch (JsonProcessingException e) {
                log.error(e.getMessage());
            }
            return json != null ? json : "";
        }
    }

    enum Document_Format {
        MANUAL,
        CSV,
        XML
    }

    public enum Type {
        LP_INTRODUCE_GOODS
    }

}

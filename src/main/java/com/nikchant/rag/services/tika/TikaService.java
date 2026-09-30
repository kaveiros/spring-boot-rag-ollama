package com.nikchant.rag.services.tika;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.stereotype.Service;

@Service 
public class TikaService {

    public TikaService(){}
    

    public List<Document> readDocument(String documentName) {
        TikaDocumentReader reader = new TikaDocumentReader(documentName);
        return  reader.read();
        
    }
}

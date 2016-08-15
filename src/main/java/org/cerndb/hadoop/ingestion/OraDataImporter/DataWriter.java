// Copyright (C) 2016, CERN
// This software is distributed under the terms of the GNU General Public
// Licence version 3 (GPL Version 3), copied verbatim in the file "LICENSE".
// In applying this license, CERN does not waive the privileges and immunities
// granted to it by virtue of its status as Intergovernmental Organization
// or submit itself to any jurisdiction.


package org.cerndb.hadoop.ingestion.OraDataImporter;


import org.cerndb.oracle.util.OraDataDecoder;



//Exceptions
import java.text.ParseException;
import java.lang.NumberFormatException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;



//Utils
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;



import java.nio.charset.StandardCharsets;




//Kite related

import org.kitesdk.data.Dataset;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.kitesdk.data.Dataset;
import org.kitesdk.data.DatasetDescriptor;
import org.kitesdk.data.DatasetWriter;
import org.kitesdk.data.Datasets;
import org.kitesdk.data.Formats;
import org.kitesdk.data.CompressionType;
import static org.apache.avro.generic.GenericData.Record;

   class DataWriter{

	private DatasetDescriptor dsd;
	private  Dataset<Record> ds;
	DatasetWriter<Record> writer;
	GenericRecordBuilder builder;
        private String DataSetURI,Schema;
	private  Map<Integer, HashMap<String,String>> RowSchema;

       DataWriter(String URI){
	DataSetURI = URI;
	}

	DataWriter( Map<Integer, HashMap<String,String>> RowS) {
		RowSchema = RowS;
	}

       public void setDataSetURI(String URI) {DataSetURI = URI;}
       public void setAvroSchema(String AvroSchema) {Schema = AvroSchema;}


       public void CreateDataset() throws IOException{

		ds = Datasets.create(
	        DataSetURI, dsd, Record.class);
		System.out.println("Dataset created");		
		
       }
       public void InitDataset(String URI,String AvroSchema) throws IOException
       {
		//Initializing fields
		 DataSetURI=URI;
		 Schema=AvroSchema;

		dsd = new DatasetDescriptor.Builder()
                .schemaLiteral(AvroSchema)
                .format(Formats.PARQUET)
                .compressionType(CompressionType.Snappy)
                .build();

		synchronized(this){
			try{
			//check if exists
				ds=Datasets.load(DataSetURI,Record.class);
			}
			catch(Exception e)
			{	
				CreateDataset();
			}
		}
       }

       public DatasetWriter<Record> openWriter(){
		builder = new GenericRecordBuilder(dsd.getSchema());
		writer=ds.newWriter();
                return writer;
	}
       public void write(Record r){
		writer.write(r);
	}
        public void write(String col1,String col2,String col3){
		Record record = builder.set("col1", col1)
            .set("col2", col2)
            .set("col3",col3).build();
	        writer.write(record);
	}


	public void write(byte[][] cols)
	{
		for (int i=0; i<RowSchema.size(); i++)
		{
			try{
				builder.set(RowSchema.get(i+1).get("name"),OraDataDecoder.castColType(cols[i],RowSchema.get(i+1).get("type")));
			}
			catch(ArrayIndexOutOfBoundsException e)
			{
				System.out.println(new String(cols[0],StandardCharsets.UTF_8));
				throw e;
			}
			catch (NullPointerException npe)
			{
				System.out.println(new String(cols[0],StandardCharsets.UTF_8));

				throw npe;
			}
		}
		writer.write(builder.build());
		
	}

        public void close(){
		writer.close();
	}
   }

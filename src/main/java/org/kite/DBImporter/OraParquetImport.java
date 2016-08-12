package org.kite.DBImporter;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import java.text.SimpleDateFormat;

//Exceptions
import java.text.ParseException;
import java.lang.NumberFormatException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;



import oracle.sql.*; 

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import java.lang.Integer;
import java.lang.Double;
import java.util.Date;
import java.nio.charset.StandardCharsets;


import org.kitesdk.data.Dataset;

import java.util.Random;
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
import  java.math.BigInteger;

import java.util.HashMap;
import java.util.Map;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class OraParquetImport{

	private static final int THREADS = Integer.parseInt(System.getProperty("parallel", "1"));
	private static final String SQL = System.getProperty("sql", "Null");
	private static final String OUTPUT_PATH = System.getProperty("outputDir", "Null");
	private static final String DB_URI = System.getProperty("jdbcURI", "Null");
	private static final String DB_SCHEMA = System.getProperty("schema", "Null");
	private static final String DB_PASS = System.getProperty("password", "Null");


        //checks


        public static void main(String[] argv) {

                OraParquetImport imp = new OraParquetImport();
		
		//parameter checks
		if(SQL.equals("Null")||OUTPUT_PATH.equals("Null")||DB_URI.equals("Null")||DB_SCHEMA.equals("Null")||DB_PASS.equals("Null"))
		{
			System.out.println("Not enough parameters provided");
			System.out.println("THREADS: " +THREADS);
                        System.out.println("SQL: " +SQL);
                        System.out.println("DBURI: " +DB_URI);
                        System.out.println("DB_SCHEMA: " +DB_SCHEMA);
			if (OUTPUT_PATH.equals("Null"))
	                        System.out.println("DB_PASS: " +DB_PASS);
			else
				System.out.println("DB_PASS: ********");

			printHelp();
                        System.exit(1);

		}
                try{
                	imp.run(argv);
                }
		 catch(IOException ioe)
            {System.out.println(ioe.getMessage());}

		


        }
	public static void printHelp()
	{
		String text="\n"+
"Usage: Command -Dsql=<text> -DjdbcURI=<text> -DoutputDir=<text> [-D..options..]";
		System.out.println(text);
	}
        public void run(String[] argv) throws IOException, NumberFormatException
        {




  //                SQL= "select * from data_vectornumeric";
//		SQL="select * from meetup.data_numeric where utc_stamp between trunc(sysdate-3) and trunc(sysdate-2)";
//		SQL="select * from lhclog.data_numeric where utc_stamp between trunc(sysdate-3) and trunc(sysdate-2)";
//		SQL="select 355.34666 a, -234.76421 b,.00005091 c, 10000 d from dual";
//		SQL="select * from sys.test_v ";
//		SQL="select rowid,variable_id,utc_stamp,value from lhclog.data_vectornumeric partition(PART_DVN_20160702)";




		DBConnect connection = new DBConnect();
		connection.OraConnect(DB_URI,DB_SCHEMA,DB_PASS);
                
		ResultSet rs = null;

		try{

		   connection.setDirectReads(true);

		   rs = connection.execute(SQL);

	           SyncDataSource sds = new SyncDataSource(rs);	
		
                   System.out.println("Starting threads");

		   List<WorkerThread> threadList = new ArrayList<WorkerThread>();

                   for (int i=0;i<THREADS;i++)
                   {
                        WorkerThread t = new WorkerThread("Thread-"+i,sds,OUTPUT_PATH);
			threadList.add(t);
                        t.start();
                        if (i==0&&THREADS>1)
                        {
				//wait to initialize dataset by the first thread
			     try{

				Thread.sleep(5000);
			     }
                             catch (InterruptedException e){}

				
                        }

                   }
		   
		   for (WorkerThread th : threadList)
		   {
			 try{
                            th.join();
                          }
                           catch (InterruptedException e){}

		   }

                }
                catch (SQLException e) {
                        System.out.println("Failed to execute statement! "+ e.toString());
                        System.exit(1);
                }
                //catch(ParseException e)
//              {}
                finally {
                     connection.close();
                     System.out.println("Job finished");
                }
        }

        public static void reportStats(long start,long end,int value) {
                long delta = end-start;
                System.out.println( value + " rows read in "+delta+"ms AVG:"+String.format("%.02f", value/((float)delta/1000))+" rows per sec");

        }
   
   class DBConnect
   {
	Connection connection=null;
	PreparedStatement pstmt=null;
	DBConnect()
	{}
	
	public void OraConnect(String URI,String schema,String password)
	{
		try {

                        Class.forName("oracle.jdbc.driver.OracleDriver");

                } catch (ClassNotFoundException e) {

                        System.out.println("Where is your Oracle JDBC Driver not found");
                        e.printStackTrace();
                        System.exit(1);

                }
		try {

                        connection = DriverManager.getConnection(URI,schema,password);

                } catch (SQLException e) {

                        System.out.println("Connection Failed!");
                        e.printStackTrace();
                        System.exit(1);

                }

                if (connection != null) {
                } else {
                        System.out.println("Failed to make connection!");
                        System.exit(1);
                }

		
		

	}
	public ResultSet execute(String sql) throws SQLException
	{
	     pstmt = connection.prepareStatement(sql);
             if(pstmt.execute())
  	        return pstmt.getResultSet();
             return null;
	}	
	public void setDirectReads(boolean direct) throws SQLException
	{
		if (direct)
			execute("alter session set \"_serial_direct_read\"=always");
		else 
			execute("alter session set \"_serial_direct_read\"=false");

	}
	public void close()
	{
	        try{

			pstmt.close();
		}
		catch(SQLException e)
		{}
		finally
		{
			try{
				connection.close();		
			}
			catch(SQLException e)
			{}
		}
	}
	
   }
   class SyncDataSource
   {

        private ResultSet rs;
        public int ncols;
        public Map<Integer, HashMap<String,String>> RowSchema = new HashMap<Integer, HashMap<String,String>>();

        SyncDataSource(ResultSet r) throws SQLException
        {
           rs = r;
	   getRowSchema(rs);
           getAvroSchema();
        }

        
        
        public boolean next(byte[][] cols,boolean getData)  throws SQLException
        {
            boolean ret = false;

            synchronized(this)
            {
               ret = rs.next();
		if (ret&&getData)
		{
	                cols[0]=rs.getBytes(1);
	                cols[1]=rs.getBytes(2);
	                cols[2]=rs.getBytes(3);
		}
            }
            return ret;
        }

   

       public int next(byte[][][] cols,int batchSize,boolean getData)  throws SQLException
        {

            int ret = 0;

            synchronized(this)
            {
		for(int i=0;i<batchSize;i++)
		{
	                if (rs.next()&getData)
        	        {
                	        cols[i][0]=rs.getBytes(1);
                        	cols[i][1]=rs.getBytes(2);
                        	cols[i][2]=rs.getBytes(3);
                                ret++;
                	}
		}
            }
            return ret;
        }


	public int nextRow(byte[][][] cols,int batchSize,boolean getData)  throws SQLException
        {

            int ret = 0;

            synchronized(this)
            {
                for(int i=0;i<batchSize;i++)
                {
                        if (rs.next()&getData)
                        {
				for(int j=0; j<ncols;j++)
                                	cols[i][j]=rs.getBytes(j+1);
                                ret++;
                        }
                }
            }
            return ret;
        }

        private void getRowSchema(ResultSet resultSet ) throws SQLException
	{
		ResultSetMetaData meta = resultSet.getMetaData();
                ncols = meta.getColumnCount();
                System.out.println("Number of columns: "+ncols);
		HashMap<String,String> rowDetails;
		for (int i=1; i<=ncols;i++)
                {
		  
                   rowDetails = new HashMap<String,String>();
		   rowDetails.put("name",meta.getColumnLabel(i));
                   rowDetails.put("type", meta.getColumnTypeName(i));
		   try{
                   	Class test = Class.forName(meta.getColumnClassName(i));
		   }
		   catch(ClassNotFoundException e){
			System.out.println(e.getMessage());

				
		   }
		   rowDetails.put("class", mapClassName(meta.getColumnClassName(i)));
                   rowDetails.put("avro",mapType2Avro(meta.getColumnClassName(i),meta.getColumnLabel(i)));
               
                       
		   RowSchema.put(i, rowDetails);
		   System.out.println(i+": "+RowSchema.get(i).get("name")+", "+RowSchema.get(i).get("type")+", "+RowSchema.get(i).get("class")+", "+RowSchema.get(i).get("avro"));

                }

	}
	private String mapClassName(String cls)
	{
		if (cls=="oracle.jdbc.OracleArray")
			return "java.sql.Array";

		return cls;
	}
        public String getAvroSchema() throws SQLException
	{
	     
	     String namespace="cern.ch";
             String recordName="record";
	     String recordType="record";
            
	     String AvroSchema="{"+
		"  \"namespace\" : \""+namespace+"\","+
		"  \"name\": \""+recordName+"\","+
		"  \"type\" :  \""+recordType+"\","+
		"  \"fields\" :[";

             for (int i=0; i<RowSchema.size();i++)
	     {
		if(i!=0) AvroSchema+=",";

		AvroSchema+="{\"name\": \""+RowSchema.get(i+1).get("name")+"\", \"type\":"+RowSchema.get(i+1).get("avro")+"}";
	     }
             AvroSchema+="]}";
		

             System.out.println(AvroSchema);
	     return AvroSchema;
		
	}
        private String mapType2Avro(String type,String name)
	{
		String ret=null;

//		System.out.println(type);
                if(type.equals("oracle.jdbc.OracleArray")) ret= "{\"type\": \"array\", \"items\": \"double\"}";

		if(name.equals("VARIABLE_ID")) {
			ret= "\"long\"";
		}



		if (type=="java.sql.Timestamp"||(type=="java.math.BigDecimal"&&name!="VARIABLE_ID")||type=="oracle.sql.TIMESTAMPTZ"||type=="oracle.sql.TIMESTAMP") ret= "\"double\"";
		if (type=="java.lang.String") ret= "\"string\"";

		if(type.equals("java.sql.Array")) ret= "{\"type\": \"array\", \"items\": \"double\"}";

		  if(name.equals("VARIABLE_ID")) {
                        ret= "\"long\"";
                }



		if (ret==null)
			ret= "\"string\"";
		return "["+ret+",\"null\"]";
	}
	

   }


   class WorkerThread implements Runnable {

      private Thread t;
      private String threadName;
      private SyncDataSource sds;
      private DataWriter dw;
      private boolean writeData=true;
      private boolean readData=true;
      private int batchSize=10000;
      private String DatasetURI;

      WorkerThread(String name,SyncDataSource s,String URI) throws IOException{
        threadName = name;
        sds=s;
	DatasetURI=URI;
        System.out.println("Creating " +  threadName );
      }

      public void join() throws InterruptedException
      {
        t.join();
      }
      
	public void run() {
         System.out.println("Running " +  threadName );
	

            try{

		     dw = new DataWriter(sds.RowSchema);
                     dw.InitDataset(DatasetURI, sds.getAvroSchema());
		     dw.openWriter();          
	    }
	    catch(IOException ioe)
	    {
		System.out.println(ioe.getMessage());
		return;
	    }
            catch (SQLException sqle)
	    {
		System.out.println(sqle.getMessage());
		return;
	    }
            try{
 
            
              if (batchSize>1) {//BATCH MODE
                 byte[][][] rows = new byte[batchSize][sds.ncols][];
		 String col1,col2,col3;
                 int rows_num=batchSize;

	     try{
                 while(rows_num == batchSize){ 
			rows_num = sds.nextRow(rows, batchSize,readData);
			for(int i = 0; i < rows_num; i++){
				byte[][] rowcols = new byte[sds.ncols][];
				for(int j=0; j <sds.ncols; j++){
					
                                        Class rowClass = Class.forName(sds.RowSchema.get(j+1).get("class"));
					//rowcols[j] = rowClass.cast(rows[i][j]);
                                        rowcols[j] =  rows[i][j];
                                        
				}
//			     col1 = new String(rows[i][0], StandardCharsets.UTF_8);
//                             col2 = new String(rows[i][1], StandardCharsets.UTF_8);
//                             col3 = new String(rows[i][2], StandardCharsets.UTF_8);

                               dw.write(rowcols);

			} 
		}
	       }//try
	       catch (ClassNotFoundException cnfe)
		{
			System.out.println(cnfe.getMessage());
			
			return;
		}
		
	      }
              else {//CLASIC MODE
		 byte[][] cols = new byte[3][];

                 String col1,col2,col3;
		 
                 while(sds.next(cols,readData)) {
             
		     if(writeData)
		     {
	                     col1 = new String(cols[0], StandardCharsets.UTF_8);
	                     col2 = new String(cols[1], StandardCharsets.UTF_8);
	                     col3 = new String(cols[2], StandardCharsets.UTF_8);
		     
		     	     dw.write(col1,col2,col3);
		     }
                     cols=new byte[3][];

                  }//while
                }//else
              }//try            
              catch (SQLException e) {
                        System.out.println("Failed to execute statement! "+ e.toString());


              }
		finally {
      			if (dw != null) {
		            dw.close();
		        }
		}  


     }



      

      public void start ()
      {
          System.out.println("Starting " +  threadName );
          if (t == null)
          {
            t = new Thread (this, threadName);
            t.start ();

          }

      }


   }
}
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

		try{
			//check if exists
			ds=Datasets.load(DataSetURI,Record.class);
		}
		catch(Exception e)
		{	
			CreateDataset();
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
				builder.set(RowSchema.get(i+1).get("name"),castColType(cols[i],RowSchema.get(i+1).get("type")));
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

        private Object castColType(byte[] data,String type)
	{
		Object odata=null;
		switch(type)
		{
			case "CHAR":
				odata = castVarchar(data);
				break;
			case "NUMBER":
				NUMBER n = new NUMBER(data);
				odata=n.doubleValue();
				break;
			case "TIMESTAMP":
				odata = castTimestamp(data);
				break;
			case "LHCLOG.VECTORNUMERIC":
				//ARRAY v = Datum.Datum(data);

				//odata = Arrays.asList(((ARRAY)v).getDoubleArray());

				odata = castArray(data,"NUMRIC");
				break;

		}
		return odata;
		
	}
/*	private Object castType(byte[] data,String type)
        {
		Object cdata=null;
		if(type.equals("CHAR")) cdata = castVarchar(data);
		else
		if(type.equals("NUMBER")) cdata = castNumber(data);
		else
                if(type.equals("TIMESTAMP")) cdata = castTimestamp(data);
		else 
		if (type.equals("LHCLOG.VECTORNUMERIC")) cdata = castArrayofNumbers(data);
		else cdata=(Object)data;

		return cdata;
	}
*/
	 private List<Object> castArray(byte[] data,String type)
	 {
		OraArray arr = new OraArray(data,type);
		return arr.elements;
	 }
/*	 private List<Object> castArrayofNumbers(byte[] data){
	//	System.out.println(new String(data,StandardCharsets.UTF_8));
//                return (Object)new String(data,StandardCharsets.UTF_8);
		int value=0;
		int elementLength=0;
		List<Object> list = new ArrayList<Object>();
		try{
			for (int i=getArrayStartPos(data);i<data.length;i++)
			{
				
				value=getInt(data[i]);
			        	
				//System.out.println(value);	
				list.add( castNumber(Arrays.copyOfRange(data, i+1, i+value+1)) );
				i+=value;
			}
			//System.out.println(list);

		} catch(NumberFormatException ex)
		{
			System.out.println(ex.getMessage());	
			dumpRawArray(data);
			throw ex;
		}
		catch (NullPointerException npe)
		{
			System.out.println(npe.getMessage());
                        dumpRawArray(data);
			throw npe;

		}
			return  list;
        }
*/
	private Object castVarchar(byte[] data){
		return (Object)new String(data,StandardCharsets.UTF_8);
	}
        public static Object castNumber(byte[] data){
		
		boolean positive = true;
		int exp=0;
		int value=0;
		String number=null;
              

		for (int i=0;i<data.length;i++)
		{
			value=getInt(data[i]);
//			System.out.println(value);
			if(i==0)
			{
			   if (value==128) return 0;
			   if (value>127)
			   {
				exp=value-192;
				number="";
					
			   }
			   else 
			   {
//				System.out.println();
				positive=false;
				exp=63-value;
				number="-";

			   }
			   if (exp<0)
			   {
				number=String.format("%."+(-exp*2)+"f",0.0);
			   }
			   continue;
			}
			if(exp==i-1) number+=".";
			if(positive)
			{
				number+=String.format("%02d",value-1);
			}
			else{
				if(i!=data.length-1)
					number+=String.format("%02d",101-value);
				
			}
						
			
		}
		if(number!=null&&exp>(data.length-1))
		     for(int j=data.length-1;j<exp;j++) number+="00";
		return (Object) Double.valueOf(number);
	}
	public static int getInt(byte b)
	{
	        return b & 0xFF;
		
	}
        private Object castTimestamp(byte[] data){
                String date="";
		double nano;
		long unixtime=0;
		int value=0;
		byte[] nanoS= new byte[4];
                for (int i=1;i<data.length;i++){
			value=getInt(data[i]);
			switch(i){
				case 1: date+= String.format("%02d", value - 100 );
					break;
				case 2: date+= String.format("%02d",value);
					break;
				case 3: date+= String.format("%02d",value);
					break;
				case 4: date+= String.format("%02d",value - 1);
					break;
				case 5: date+= String.format("%02d",value - 1);
					break;
				case 6: date+= String.format("%02d",value - 1);
					break;
				default:
					nanoS[i-7]=data[i];	
					break;
			}
		}
		nano=(new BigInteger(nanoS).intValue())
			/(double)1000000;

		DateFormat dfm = new SimpleDateFormat("yyMMddHHmmss"); 
		dfm.setTimeZone(TimeZone.getTimeZone("GMT"));
		try
    		{
			//timestamp in ms
		        unixtime = dfm.parse(date).getTime();  

			
		} 
		catch (ParseException e) 
		{
		        e.printStackTrace();
		}		
//		System.out.println(date+"="+(unixtime+nano));
                return (Object) (unixtime+nano);

        }

        public void close(){
		writer.close();
	}
   }
   class OraArray
   {
	private byte[] raw;
	public List<Object> elements;
	private int arraySize=0;
	public int length=0;
	public String type = null;

	OraArray(byte[] data,String type)
	{
		raw=data;
		parse(raw,type);
	}
	public  void dumpRawArray(byte[] data)
       	{
               	for(int i = 0; i < data.length ; i ++ ){
                       	System.out.println(i+"/"+data.length+": "+getInt(data[i]));
		}              	
        }

	private void parse(byte[] data,String type)
	{
		int state = 0;
		int v=0;
		elements = new ArrayList<Object>();
		for(int i=2;i<data.length;i++)
		{
			v=getInt(data[i]);
			switch(state){
				case 0: //getting length
					if(v==254)
					{
					    arraySize=(new BigInteger(Arrays.copyOfRange(data,i+1,i+5))).intValue();
					    i+=4;
					}
					else
					    arraySize=v;
					state=1;
			                break;
				case 1: //length initialized
					if(arraySize==data.length) state=2;
					else {
	                                         dumpRawArray(data);
						 v=data[-1];  //something went wrong,raise an exception
						 dumpRawArray(data);
					}
					i+=4; //we do not know what the next number is
					break;
				case 2: //geting elements number						
					if(v==254)
                                               {
                                                  length=(new BigInteger(Arrays.copyOfRange(data,i+1,i+5))).intValue();
                                                   i+=4;
                                               }
                                               else
                                                  length=v;
                                               state=3;
                                               break;

				case 3: //getting elements value
					elements.add(DataWriter.castNumber(Arrays.copyOfRange(data, i+1, i+v+1)));
					i+=v;
					break;
			}
		}
		//check
		
		if (elements.size()!=length) {
			System.out.println(elements.size()+"!="+length);
			 dumpRawArray(data);
			 v=data[-1];
		}
	
		
      }
	 private int getInt(byte b)
        {
                return b & 0xFF;
	}

	
   	    
   }


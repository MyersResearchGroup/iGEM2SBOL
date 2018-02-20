package org.sbolstandard.iGEM2SBOL;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import javax.xml.namespace.QName;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.Activity;
import org.sbolstandard.core2.Collection;
import org.sbolstandard.core2.Component;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.OrientationType;
import org.sbolstandard.core2.Range;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidate;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.Sequence;
import org.sbolstandard.core2.SequenceAnnotation;
import org.sbolstandard.core2.SequenceOntology;
import org.synbiohub.frontend.SynBioHubException;
import org.synbiohub.frontend.SynBioHubFrontend;

public class iGEM2SBOL 
{
	private static String uriPrefix = "http://igem.org/"; 
	private static String version = "1";
	private static String so = "http://identifiers.org/so/";
	private static String provNS = "http://www.w3.org/ns/prov#";
	private static String dcNS = "http://purl.org/dc/elements/1.1/";
	private static String dcTermsNS = "http://purl.org/dc/terms/";
	private static String igemTermNS = "http://wiki.synbiohub.org/wiki/Terms/igem#";
	private static String igemNS = "https://synbiohub.org/user/myers/igem/";
//	private static String oboNS = "http://purl.obolibrary.org/obo/";
	private static String sbhTermNS = "http://wiki.synbiohub.org/wiki/Terms/synbiohub#";
	
	private static HashMap<String,JSONObject> bioBricks = new HashMap<String,JSONObject>();
	private static HashMap<Long,JSONObject> partIds = new HashMap<Long,JSONObject>();
	private static HashMap<Long,JSONObject> featureMap = new HashMap<Long,JSONObject>();
	private static JSONObject sprot;
	
	static URI activityURI;
	static String createdDate;
	
	private static ComponentDefinition partToComponentDefinition(SBOLDocument document, JSONObject part, 
			boolean root,boolean dropMutableDescription) throws SBOLValidationException 
	{
		// componentDefinition.displayId = part.part_name.replace(/[\W]+/g,"_");
		String displayId = (String)part.get("part_name");
		displayId = displayId.replace("-", "_");
		ComponentDefinition cd = document.getComponentDefinition(displayId,version);
		if (cd!=null) return cd;
		cd = document.createComponentDefinition(displayId, version, ComponentDefinition.DNA);
		cd.addWasGeneratedBy(URI.create(uriPrefix+"/user/myers/igem/igem2sbol/1"));
		cd.addWasDerivedFrom(URI.create("http://parts.igem.org/Part:" + (String)part.get("part_name").toString()));
		if (((String)part.get("nickname")).equals("")) {
			cd.setName((String)part.get("part_name"));
		} else {
			cd.setName((String)part.get("nickname"));
		}
		JSONArray sprotMapping = (JSONArray)sprot.get(part.get("part_id")+"_"+part.get("part_name"));
		if (sprotMapping != null) {
			for (Object accessionObj : sprotMapping) {
				String accession = (String)accessionObj;
				cd.createAnnotation(new QName("http://www.w3.org/2000/01/rdf-schema#","seeAlso","rdfs"), 
						URI.create("http://www.uniprot.org/uniprot/" + accession));	
				System.out.println("Adding " + accession.toString());
			}
		}
		cd.setDescription((String)part.get("short_desc"));
		if (!((String)part.get("description")).equals("")&&!dropMutableDescription) {
			cd.createAnnotation(new QName(sbhTermNS,"mutableDescription","sbh"), (String)part.get("description"));
		}
		if (!((String)part.get("author")).equals("")) {
			cd.createAnnotation(new QName(dcNS,"creator","dc"), (String)part.get("author"));
		}
		if (!((String)part.get("status")).equals("")) {
			cd.createAnnotation(new QName(igemTermNS,"status","igem"), 
					URI.create(igemTermNS+"status/"+(String)part.get("status")));
		}
		if (!((String)part.get("part_status")).equals("")) {
			cd.createAnnotation(new QName(igemTermNS,"partStatus","igem"), (String)part.get("part_status"));
		}
		if (!((String)part.get("sample_status")).equals("")) {
			cd.createAnnotation(new QName(igemTermNS,"sampleStatus","igem"), (String)part.get("sample_status"));
		}
		if (((Long)part.get("dominant"))==0) {
			cd.createAnnotation(new QName(igemTermNS,"dominant","igem"), false);
		} else {
			cd.createAnnotation(new QName(igemTermNS,"dominant","igem"), true);
		}
		if (((Long)part.get("discontinued"))==0) {
			cd.createAnnotation(new QName(igemTermNS,"discontinued","igem"), false);
		} else {
			cd.createAnnotation(new QName(igemTermNS,"discontinued","igem"), true);
			Long discontinued = (Long)part.get("discontinued");
			if (discontinued > 1) {
				cd.createAnnotation(new QName(dcTermsNS,"isReplacedBy","dcterms"), 
						URI.create(igemNS+(String)partIds.get(discontinued).get("part_name")+"/1"));
			}
		}
		if (((Long)part.get("favorite"))==0) {
			cd.createAnnotation(new QName(sbhTermNS,"bookmark","sbh"), false);
		} else {
			cd.createAnnotation(new QName(sbhTermNS,"bookmark","sbh"), true);
		}
		if (((Long)part.get("rating"))==0) {
			cd.createAnnotation(new QName(sbhTermNS,"star","sbh"), false);
		} else {
			cd.createAnnotation(new QName(sbhTermNS,"star","sbh"), true);
		}
		// Needed?
		cd.createAnnotation(new QName(igemTermNS,"owning_group_id","igem"), (Long)part.get("owning_group_id"));
		cd.createAnnotation(new QName(igemTermNS,"owner_id","igem"), (Long)part.get("owner_id"));
		cd.createAnnotation(new QName(igemTermNS,"m_user_id","igem"), (Long)part.get("m_user_id"));
		cd.createAnnotation(new QName(igemTermNS,"group_u_list","igem"), (String)part.get("group_u_list"));
		if (!((String)part.get("source")).equals("")) {
			cd.createAnnotation(new QName(sbhTermNS,"mutableProvenance","sbh"), (String)part.get("source"));
		}
		if (!((String)part.get("notes")).equals("")) {
			cd.createAnnotation(new QName(sbhTermNS,"mutableNotes","sbh"), (String)part.get("notes"));
		}
		if (!((String)part.get("creation_date")).equals("")) {
			cd.createAnnotation(new QName(dcTermsNS,"created","dcterms"), (String)part.get("creation_date"));
		}
		if (!((String)part.get("m_datetime")).equals("")) {
			cd.createAnnotation(new QName(dcTermsNS,"modified","dcterms"), (String)part.get("m_datetime"));
		}
		if (!((String)part.get("works")).equals("")) {
			cd.createAnnotation(new QName(igemTermNS,"experience","igem"), 
					URI.create(igemTermNS+"experience/"+(String)part.get("works")));
		}
		if (!((String)part.get("part_type")).equals("")) {
			cd.addRole(URI.create(igemTermNS + "partType/" + (String)part.get("part_type")));
			URI roleURI = getRole((String)part.get("part_type"));
			if (roleURI!=null) {
				cd.addRole(roleURI);
			}
		} else {
            cd.addRole(SequenceOntology.SEQUENCE_FEATURE);
		}
		if (!((String)part.get("sequence")).equals("")) {
			Sequence seq = document.createSequence(displayId+"_sequence", (String)part.get("sequence"), Sequence.IUPAC_DNA);
			seq.setWasDerivedFroms(cd.getWasDerivedFroms());
			seq.setWasGeneratedBys(cd.getWasGeneratedBys());
			cd.addSequence(seq);
		}
		JSONArray features = (JSONArray)part.get("features");
		if (features!=null) {
			for (Object featureObj : features) {
				extractFeatures(document,cd,part,(Long)featureObj,dropMutableDescription);
			}
		}
		return cd;
	}
	
	private static void extractFeatures(SBOLDocument document,ComponentDefinition cd,JSONObject part,
			Long featureId,boolean dropMutableDescription) throws SBOLValidationException {
		ComponentDefinition featureComponentDefinition = null;
		JSONObject feature = featureMap.get(featureId);
		if (((Long)feature.get("start_pos"))==0 || ((Long)feature.get("end_pos"))==0) return;
		if (!((String)part.get("specified_u_list")).equals("_" + (Long)part.get("part_id")+"_") &&
				((String)feature.get("feature_type")).equals("BioBrick")) {
			if (((String)feature.get("label")).equals((String)part.get("part_name"))) return;
			JSONObject subpart = bioBricks.get((String)feature.get("label"));
			if (subpart==null) {
				System.out.println("could not find subpart " + (String)feature.get("label"));
				//return;
			} else {
				if (!((String)part.get("specified_u_list")).contains("_"+subpart.get("part_id")+"_")&&
						!((String)part.get("deep_u_list")).contains("_"+subpart.get("part_id")+"_")) return;
				featureComponentDefinition = partToComponentDefinition(document, subpart, false, dropMutableDescription);
			}
		} else if (!((String)part.get("specified_u_list")).equals("_" + (Long)part.get("part_id")+"_") &&
				!((String)feature.get("feature_type")).equals("BioBrick")) {
			return;
		}
		String labelOrType = "";
		if (!((String)feature.get("label")).equals("")) {
			labelOrType = (String)feature.get("label");
		} else if (!((String)feature.get("label2")).equals("")) {
			labelOrType = (String)feature.get("label2");
		} else if (!((String)feature.get("type")).equals("")) {
			labelOrType = (String)feature.get("type");
		} else if (!((String)feature.get("feature_type")).equals("")) {
			labelOrType = (String)feature.get("feature_type");
		} else {
			labelOrType = (String)feature.get("feature_id");
		}
		Component comp = null;
		if (featureComponentDefinition!=null) {
			comp = cd.createComponent("component"+featureId, AccessType.PUBLIC, featureComponentDefinition.getIdentity());
			comp.setName(labelOrType);
		}
		int start = Integer.valueOf(feature.get("start_pos").toString());
		int end = Integer.valueOf(feature.get("end_pos").toString());
		if (start > end) {
			System.out.println("Start = " + start + " > End = " + end + " swapping");
			int temp = start;
			start = end;
			end = temp;
		}
		SequenceAnnotation sa = cd.createSequenceAnnotation("annotation"+featureId, "range", 
				start, end, OrientationType.INLINE);
		sa.setName(labelOrType);
		if (comp!=null) {
			sa.setComponent(comp.getIdentity());
		} else {
			if (!((String)feature.get("feature_type")).equals("")) {
				sa.addRole(URI.create(igemTermNS+"feature/"+feature.get("feature_type")));
				URI roleURI = getRole((String)feature.get("feature_type"));
				if (roleURI!=null) {
					sa.addRole(roleURI);
				}
			} else {
	            sa.addRole(SequenceOntology.SEQUENCE_FEATURE);
			}
		}
	}
	
	private static URI getRole(String type) {
		if (type.equals("Basic")) {
			return URI.create(so + "SO:0000316");
		} else if (type.equals("Cell")) {
			return URI.create(so + "SO:0000340");
		} else if (type.equals("Coding")) {
			return URI.create(so + "SO:0000316");
		} else if (type.equals("Composite")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("Conjugation")) {
			return URI.create(so + "SO:0000724");
		} else if (type.equals("Device")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("DNA")) {
			return URI.create(so + "SO:0000110");
		} else if (type.equals("Generator")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("Intermediate")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("Inverter")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("Measurement")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("Other")) {
			return URI.create(so + "SO:0000110");
		} else if (type.equals("Plasmid")) {
			return URI.create(so + "SO:0000155");
		} else if (type.equals("Plasmid_Backbone")) {
			return URI.create(so + "SO:0000755");
		} else if (type.equals("Primer")) {
			return URI.create(so + "SO:0000112");
		} else if (type.equals("Project")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("Protein_Domain")) {
			return URI.create(so + "SO:0000417");
		} else if (type.equals("RBS")) {
			return URI.create(so + "SO:0000139");
		} else if (type.equals("Regulatory")) {
			return URI.create(so + "SO:0000167");
		} else if (type.equals("Reporter")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("RNA")) {
			return URI.create(so + "SO:0000834");
		} else if (type.equals("Scar")) {
			return URI.create(so + "SO:0001953");
		} else if (type.equals("Signalling")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("T7")) {
			return URI.create(so + "SO:0001207");
		} else if (type.equals("Tag")) {
			return URI.create(so + "SO:0000324");
		} else if (type.equals("Temporary")) {
			return URI.create(so + "SO:0000110");
		} else if (type.equals("Terminator")) {
			return URI.create(so + "SO:0000141");
		} else if (type.equals("Translational_Unit")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("barcode")) {
			return URI.create(so + "SO:0000807");
		} else if (type.equals("binding")) {
			return URI.create(so + "SO:0001091");
		} else if (type.equals("BioBrick")) {
			return URI.create(so + "SO:0000804");
		} else if (type.equals("dna")) {
			return URI.create(so + "SO:0000110");
		} else if (type.equals("misc")) {
			return URI.create(so + "SO:0000110");
		} else if (type.equals("mutation")) {
			return URI.create(so + "SO:0001059");
		} else if (type.equals("polya")) {
			return URI.create(so + "SO:0000553");
		} else if (type.equals("primer_binding")) {
			return URI.create(so + "SO:0005850");
		} else if (type.equals("protein")) {
			return URI.create(so + "SO:0000316");
		} else if (type.equals("s_mutation")) {
			return URI.create(so + "SO:1000008");
		} else if (type.equals("start")) {
			return URI.create(so + "SO:0000318");
		} else if (type.equals("stop")) {
			return URI.create(so + "SO:0000319");
		} else if (type.equals("tag")) {
			return URI.create(so + "SO:0000324");
		} else if (type.equals("promoter")) {
			return URI.create(so + "SO:0000167");
		} else if (type.equals("cds")) {
			return URI.create(so + "SO:0000316");
		} else if (type.equals("operator")) {
			return URI.create(so + "SO:0000057");
		} else if (type.equals("terminator")) {
			return URI.create(so + "SO:0000141");
		} else if (type.equals("conserved")) {
			return URI.create(so + "SO:0000330");
		} else if (type.equals("rbs")) {
			return URI.create(so + "SO:0000139");
		} else if (type.equals("stem_loop")) {
			return URI.create(so + "SO:0000313");
		} else {
			System.err.println("Part Type not found");
			return null;
		}
	}
	
	private static void createCategoryCollections(SynBioHubFrontend sbh, JSONArray parts) throws SBOLValidationException, SynBioHubException 
	{
		SBOLDocument document = new SBOLDocument(); 
		document.setDefaultURIprefix("http://igem.org"); 
		//document.setComplete(true); 
		document.setCreateDefaults(true);
		Collection categories = document.createCollection("categories","1");
		categories.setName("iGEM Parts Registry Categories");
		categories.addWasGeneratedBy(URI.create(uriPrefix+"/user/myers/igem/igem2sbol/1"));
		categories.addWasDerivedFrom(URI.create("http://parts.igem.org"));

		for (Object p : parts) {
			JSONObject part = (JSONObject)p;
			String[] cats = ((String)part.get("categories")).split(" ");
			for (String category : cats) {
				String categoryName = category.toLowerCase().replace("//","").replaceAll("-", "_");
				String[] tokens = categoryName.split("/");
				String categoryId = categoryName.replaceAll("/", "_");
				if (categoryId.trim().equals("")) continue;
				Collection collection = document.getCollection(categoryId, version);
				if (collection==null) {
					Collection parentCategory = null;
					String catId = null;
					String catName = null;
					for (String token : tokens) {
						if ( Character.isDigit(token.charAt(0))) {
							token = "_" + token;
						}
						if (parentCategory!=null) {
							catId = parentCategory.getDisplayId() + "_" + token;
							catName = parentCategory.getName() + "/" + token;
						} else {
							catId = token;
							catName = "//" + token;
						}
						collection = document.getCollection(catId, version);
						if (collection==null) {
							collection = document.createCollection(catId, version);
							collection.setName(catName);
							collection.addWasGeneratedBy(URI.create(uriPrefix+"/user/myers/igem/igem2sbol/1"));
							collection.addWasDerivedFrom(URI.create("http://parts.igem.org"));
							if (parentCategory==null) {
								categories.addMember(collection.getIdentity());
							}
						}
						if (parentCategory!=null) {
							parentCategory.addMember(collection.getIdentity());
						}
						parentCategory = collection;
					}
				}
				collection.addMember(URI.create(uriPrefix+"/user/myers/igem/"+(String)part.get("part_name")));			}
		}
		
		if (SBOLValidate.getNumErrors()>0) {
			for (String error : SBOLValidate.getErrors()) {
				System.err.println(error);
			}
		} else {   
			// Upload to SynBioHub
			sbh.addToCollection(URI.create(uriPrefix+"/user/myers/igem/igem_collection/1"), true, document);
			System.out.println("Uploaded Collections Successfully");
		}

//		try {
//			document.write(System.out);
//		}
//		catch (SBOLConversionException e) {
//			e.printStackTrace();
//		}
	}
	
	private static boolean childHasMatching(ComponentDefinition cd, SequenceAnnotation sa) 
	{
		boolean yes = false;
		for (SequenceAnnotation sibling : cd.getSequenceAnnotations()) {
			if (sa.getIdentity().equals(sibling.getIdentity())) continue;
			if (!sibling.isSetComponent()) continue;
			ComponentDefinition siblingDefinition = sibling.getComponent().getDefinition();
			for (SequenceAnnotation childAnnotation : siblingDefinition.getSequenceAnnotations()) {
				Range siblingRange = (Range)sibling.getLocation("range");
				Range childRange = (Range)childAnnotation.getLocation("range");
				int startRelativeToParent = childRange.getStart() + siblingRange.getStart() - 1;
				int endRelativeToParent = childRange.getEnd() + siblingRange.getStart() - 1;
				if (sa.getRoles().size()>0 && childAnnotation.getRoles().size()>0) {
					if (startRelativeToParent==((Range)sa.getLocation("range")).getStart() &&
							endRelativeToParent==((Range)sa.getLocation("range")).getEnd() &&
							sa.getRoles().equals(childAnnotation.getRoles())) {
								yes = true;
					}
				} else if (sa.isSetComponent() && childAnnotation.isSetComponent()) {
					if (startRelativeToParent==((Range)sa.getLocation("range")).getStart() &&
							endRelativeToParent==((Range)sa.getLocation("range")).getEnd() &&
							sa.getComponent().getDefinitionURI().equals(childAnnotation.getComponent().getDefinitionURI())) {
								yes = true;
					}
				}
			}
		}
		return yes;
	}
	
	private static void pruneTransitiveAnnotations(SBOLDocument sbol) throws SBOLValidationException 
	{
		for (ComponentDefinition cd : sbol.getComponentDefinitions()) {
			for (SequenceAnnotation sa : cd.getSequenceAnnotations()) {
				if (childHasMatching(cd,sa)) {
					if (sa.isSetComponent()) {
						Component component = sa.getComponent();
						cd.removeSequenceAnnotation(sa);
						cd.removeComponent(component);
					} else {
						cd.removeSequenceAnnotation(sa);
					}
				}
			}
		}
	}
	
	public static void main( String[] args ) throws FileNotFoundException, IOException, ParseException 
    {
		// Create an SBOLDocument
		SBOLDocument document = new SBOLDocument(); 
		document.setDefaultURIprefix("http://igem.org"); 
		document.setComplete(true); 
		document.setCreateDefaults(true);
		Activity activity = null;
		SynBioHubFrontend sbh = new SynBioHubFrontend(args[5],args[6]);
		// Create collection
		System.out.println(args[0]); // login
		System.out.println(args[1]); // password
		System.out.println(args[2]); // parts JSON file
		System.out.println(args[3]); // features JSON file
		System.out.println(args[4]); // proteins JSON file
		System.out.println(args[5]); // URL
		System.out.println(args[6]); // URI prefix
		uriPrefix = args[6];
		try {
			sbh.login(args[0], args[1]);
		}
		catch (SynBioHubException e1) {
			e1.printStackTrace();
			return;
		}
		int start = 0;
		int end = 40000;
		
		try {
			// Create an Activity
			activity = document.createActivity("igem2sbol", "1");
			activity.setName("iGEM to SBOL conversion");
			activity.setDescription("Conversion of the iGEM parts registry to SBOL2");
			activity.createAnnotation(new QName(dcNS,"creator","dc"), "Chris J. Myers");
			activity.createAnnotation(new QName(dcNS,"creator","dc"), "James Alastair McLaughlin");
			TimeZone tz = TimeZone.getTimeZone("UTC");
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			df.setTimeZone(tz);
			createdDate = df.format(new Date());
			activity.createAnnotation(new QName(provNS,"endedAtTime","prov"), createdDate);
			activityURI = activity.getIdentity();

			if (start==0)
				sbh.createCollection("igem", "1", "iGEM Parts Registry", 
						"The iGEM Registry is a growing collection of genetic parts that can be mixed and matched to build synthetic biology devices and systems.  As part of the synthetic biology community\'s efforts to make biology easier to engineer, it provides a source of genetic parts to iGEM teams and academic labs.", 
						"", true, document);
			//document.write(System.out);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Parse JSON
		JSONParser parser = new JSONParser();
		JSONArray parts = (JSONArray) parser.parse(new FileReader(args[2]));
		for (Object partObj : parts) {
			JSONObject part = (JSONObject)partObj;
			bioBricks.put((String)part.get("part_name"), part);
			partIds.put((Long)part.get("part_id"), part);
		}
		JSONArray features = (JSONArray) parser.parse(new FileReader(args[3]));
		for (Object featureObj : features) {
			JSONObject feature = (JSONObject)featureObj;
			featureMap.put((Long)feature.get("feature_id"), feature);
		}
		sprot = (JSONObject) parser.parse(new FileReader(args[4]));

		int i = 0;
		int size = parts.size();
		int success = 0;
		int failure = 0;
		for (Object p : parts) {
			i++;
			if (i < start) continue;
			if (i > end) break;
			document = new SBOLDocument(); 
			document.setDefaultURIprefix("http://igem.org"); 
			//document.setComplete(true); 
			document.setCreateDefaults(true);
			JSONObject part = (JSONObject) p;
			String displayId = (String)part.get("part_name");
			try {
				partToComponentDefinition(document,part,true,false);
				pruneTransitiveAnnotations(document);
				//document.write(System.out);
				SBOLValidate.validateSBOL(document,false,true,false);
				if (SBOLValidate.getNumErrors()>0) {
					failure++;
					System.out.println(i + " out of " + size + ":"+displayId+" FAILURE "+ failure);
					System.err.println(i + " out of " + size + ":"+displayId+" FAILURE "+ failure);
					for (String error : SBOLValidate.getErrors()) {
						System.err.println(error);
					}
				} else {   
					// Upload to SynBioHub
					sbh.addToCollection(URI.create(uriPrefix+"/user/myers/igem/igem_collection/1"), true, document);
					success++;
		        	System.out.println(i + " out of " + size + ":"+displayId+" SUCCESS "+ success);
				}
			} catch (Exception e) {
				try {
					document = new SBOLDocument(); 
					document.setDefaultURIprefix("http://igem.org"); 
					//document.setComplete(true); 
					document.setCreateDefaults(true);
					partToComponentDefinition(document,part,true,true);
					pruneTransitiveAnnotations(document);
					sbh.addToCollection(URI.create(uriPrefix+"/user/myers/igem/igem_collection/1"), true, document);
					success++;
		        	System.out.println(i + " out of " + size + ":"+displayId+" SUCCESS "+ success + " (removed mutable description)");
				}
				catch (Exception e1) {
					failure++;
		        	System.out.println(i + " out of " + size + ":"+displayId+" FAILURE "+ failure);
		        	System.err.println(i + " out of " + size + ":"+displayId+" FAILURE "+ failure);
		        	e1.printStackTrace(System.err);
				}
			}
		}
		try {
			createCategoryCollections(sbh,parts);
		}
		catch (SBOLValidationException e) {
			System.out.println("Failed to create categories");
			e.printStackTrace();
		}
		catch (SynBioHubException e) {
			System.out.println("Failed to create categories");
			e.printStackTrace();
		}
    }
}

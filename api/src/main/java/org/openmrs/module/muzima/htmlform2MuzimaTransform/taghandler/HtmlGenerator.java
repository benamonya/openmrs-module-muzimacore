package org.openmrs.module.muzima.htmlform2MuzimaTransform.taghandler;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.openmrs.module.muzima.htmlform2MuzimaTransform.Htmlform2MuzimaTransformUtil;
import org.openmrs.module.muzima.htmlform2MuzimaTransform.Translator;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Provides methods to take a {@code <htmlform>...</htmlform>} xml block and turns it into HTML to
 * be displayed as a form in a web browser. It can apply the {@code <macros>...</macros>} section,
 * and replace tags like {@code <obs/>}.
 */
public class HtmlGenerator implements TagHandler {
	
	/**
	 * Takes an XML string, finds the {@code <macros></macros>} section in it, and applies those
	 * substitutions
	 * <p/>
	 * For example the following input:
	 * <p/>
	 * <pre>
	 * {@code
	 * <htmlform>
	 *     <macros>
	 *          count=1, 2, 3
	 *     </macros>
	 *     You can count like $count
	 * </htmlform>
	 * }
	 * </pre>
	 * <p/>
	 * Would produce the following output:
	 * <p/>
	 * <pre>
	 * {@code
	 * <htmlform>
	 *     You can count like 1, 2, 3
	 * </htmlform>
	 * }
	 * </pre>
	 *
	 * @param xml the xml string to process for macros
	 * @return the xml string with after macro substitution
	 * @throws Exception
	 */
	public String applyMacros(String xml) throws Exception {
		Document doc = Htmlform2MuzimaTransformUtil.stringToDocument(xml);
		Node content = Htmlform2MuzimaTransformUtil.findChild(doc, "htmlform");
		Node macrosNode = Htmlform2MuzimaTransformUtil.findChild(content, "macros");
		
		// if there are no macros defined, we just return the original xml unchanged
		if (macrosNode == null) {
			return xml;
		}
		
		// One way to define macros is simply as the text content of the macros node.  This is left for backwards compatibility
		Properties macros = new Properties();
		String macrosText = macrosNode.getTextContent();
		if (macrosText != null) {
			macros.load(new ByteArrayInputStream(macrosText.getBytes()));
		}
		
		// Another way to define macros is as child tags to the macros node.
		NodeList children = macrosNode.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if ("macro".equals(node.getNodeName())) {
				String key = Htmlform2MuzimaTransformUtil.getNodeAttribute(node, "key", "");
				if (StringUtils.isBlank(key)) {
					throw new IllegalArgumentException("Macros must define a 'key' attribute");
				}
				String value = Htmlform2MuzimaTransformUtil.getNodeAttribute(node, "value", "");
				macros.put(key, value);
			}
		}
		
		// now remove the macros node
		content.removeChild(macrosNode);
		
		// switch back to String mode from the document so we can use string utilities to substitute
		xml = Htmlform2MuzimaTransformUtil.documentToString(doc);
		
		// substitute any macros we found
		for (Object temp : macros.keySet()) {
			String key = (String) temp;
			String value = macros.getProperty(key, "");
			xml = xml.replace("$" + key, value);
		}
		
		return xml;
	}
	
	/**
	 * Takes an XML string, finds the {@code <translations></translations>} section in it, and
	 * applies those substitutions
	 * <p/>
	 * <pre>
	 * {@code
	 * <htmlform>
	 * 		
	 *     <translations defaultLocale="en">
	 *       <code name="night_sweats">
	 *         <variant locale="en" value="night sweats"/>
	 *         <variant locale="fr" value="sueurs nocturnes"/>
	 * 		  </code>
	 *     </translations>
	 * </htmlform>
	 * } </pre> NOTE: it gets the defaultLocale specified in the form, if defaulLocale is not
	 * specified in the form, 'en' is made the defaultLocale
	 *
	 * @param xml the xml string to process for translations
	 * @return the xml string after translation substitutions have been made
	 * @throws Exception
	 */
	public String applyTranslations(String xml) throws Exception {
		Translator translator = new Translator();
		Document doc = Htmlform2MuzimaTransformUtil.stringToDocument(xml);
		Node content = Htmlform2MuzimaTransformUtil.findChild(doc, "htmlform");
		Node transNode = Htmlform2MuzimaTransformUtil.findChild(content, "translations");
		
		// if there are no translations defined, we just return the original xml unchanged
		if (transNode == null) {
			return xml;
		}
		
		String defaultLocaleStr = Htmlform2MuzimaTransformUtil.getNodeAttribute(transNode, "defaultLocale", "en");
		
		//set the default locale of the Translator to the default Locale specified in the form or English
		translator.setDefaultLocale(defaultLocaleStr);
		
		NodeList codeNodeList = transNode.getChildNodes();
		for (int i = 0; i < codeNodeList.getLength(); i++) {
			Node codeNode = codeNodeList.item(i);
			if (codeNode.getNodeName().equalsIgnoreCase("code")) {
				String codeName = Htmlform2MuzimaTransformUtil.getNodeAttribute(codeNode, "name", null);
				if (codeName == null) {
					throw new IllegalArgumentException("All translation elements must contain a valid code name");
				}
				NodeList variantNodeList = codeNode.getChildNodes();
				for (int j = 0; j < variantNodeList.getLength(); ++j) {
					Node variantNode = variantNodeList.item(j);
					if (variantNode.getNodeName().equalsIgnoreCase("variant")) {
						String localeStr = Htmlform2MuzimaTransformUtil.getNodeAttribute(variantNode, "locale",
						    defaultLocaleStr);
						
						// we are only translating to default Locale so we will only add translations that are for the default Locale
						if ((localeStr != null) && (localeStr.compareToIgnoreCase(defaultLocaleStr)) == 0) {
							
							String valueStr = Htmlform2MuzimaTransformUtil.getNodeAttribute(variantNode, "value", null);
							if (valueStr == null) {
								throw new IllegalArgumentException("All variants must specify a value");
							}
							translator.addTranslations(codeName, valueStr);
						}
					}
				}
			}
		}
		
		// now remove the trans node
		content.removeChild(transNode);
		
		// switch back to String mode from the document so we can use string utilities to substitute
		xml = Htmlform2MuzimaTransformUtil.documentToString(doc);
		xml = translator.translate(xml);
		return xml;
	}
	
	/**
	 * Takes an xml string, searches for 'comments' in the string using RegEx and filters out the
	 * comments from the input string
	 *
	 * @param xml input string
	 * @return the xml string after filtering out comments
	 * @throws Exception
	 * @should return correct xml after filtering out comments
	 */
	public String stripComments(String xml) {
		
		String regex = "<!\\s*--.*?--\\s*>"; // this is the regEx for html comment tag <!-- .* -->
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(xml);
		xml = matcher.replaceAll("");
		return xml;
	}
	
	/**
	 * Takes an xml string and removes <style>, <encounterDate>, <encounterLocation>, <encounterProvider>, <table>, <tr.>, and <td.> tags
	 * 
	 * @param xml
	 * @return
	 * @throws Exception
	 * @should return htmlform xml with <style>, <encounterDate>, <encounterLocation>, <encounterProvider>, <table>, <td>, <tr> tags removed
	 */
	public String removeUnusedNodes(String xml) throws Exception {
		xml = xml.replaceAll(
		    "<table[^(><)]*>|<td[^(><)]*>|<tr[^(><)]*>|<encounterDate[^(><)]*>|<encounterProvider[^(><)]*>|<encounterLocation[^(><)]*>|<section[^(><)]*>|<submit[^(><)]*>|</table>|</section>|</td>|</tr>|</encounterDate>|</encounterProvider>|</encounterLocation>|</submit>",
		    "");
		Document doc = Htmlform2MuzimaTransformUtil.stringToDocument(xml);
		Node content = Htmlform2MuzimaTransformUtil.findChild(doc, "htmlform");
		Node styleNode = Htmlform2MuzimaTransformUtil.findChild(content, "style");
		if (styleNode != null) {
			content.removeChild(styleNode);
		}
		xml = Htmlform2MuzimaTransformUtil.documentToString(doc);
		return xml;
	}
	
	/**
	 * Replaces &&, < and > within form with their encoded values within velocity and logic
	 * expressions (provides backwards compatibility after refactoring includeIf and excludeIf)
	 *
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	public String convertSpecialCharactersWithinLogicAndVelocityTests(String xml) throws Exception {
		
		Pattern lessThan = Pattern.compile("<");
		Pattern greaterThan = Pattern.compile(">");
		Pattern doubleAmpersand = Pattern.compile("&&");
		
		Matcher velocityMatcher = Pattern.compile("velocityTest=\"[^\"]*\"").matcher(xml);
		StringBuffer afterVelocityChanges = new StringBuffer();
		
		while (velocityMatcher.find()) {
			String str = velocityMatcher.group();
			str = doubleAmpersand.matcher(str).replaceAll("&amp;&amp;");
			str = lessThan.matcher(str).replaceAll("&lt;");
			str = greaterThan.matcher(str).replaceAll("&gt;");
			velocityMatcher.appendReplacement(afterVelocityChanges, Matcher.quoteReplacement(str));
		}
		velocityMatcher.appendTail(afterVelocityChanges);
		
		Matcher logicMatcher = Pattern.compile("logicTest=\"[^\"]*\"").matcher(afterVelocityChanges);
		StringBuffer afterLogicChanges = new StringBuffer();
		
		while (logicMatcher.find()) {
			String str = logicMatcher.group();
			str = doubleAmpersand.matcher(str).replaceAll("&amp;&amp;");
			str = lessThan.matcher(str).replaceAll("&lt;");
			str = greaterThan.matcher(str).replaceAll("&gt;");
			logicMatcher.appendReplacement(afterLogicChanges, Matcher.quoteReplacement(str));
		}
		
		logicMatcher.appendTail(afterLogicChanges);
		return afterLogicChanges.toString();
	}
	
	/**
	 * Calls the two underlying methods for handling the "<repeat>" tag
	 *
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	public String applyRepeats(String xml) throws Exception {
		xml = applyRepeatTemplateTags(xml);
		xml = applyRepeatWithTags(xml);
		return xml;
	}
	
	/**
	 * Handles the original version of the <repeat>: Takes an XML string, finds each
	 * {@code <repeat></repeat>} section in it, and applies those substitutions {@code
	 * <htmlform>
	 *   <repeat>
	 *     <template>
	 *       <obsgroup groupingConceptId="1608">
	 *         
	<tr>
	 * <td><obs conceptId="1611" answerConceptId="{conceptId}" answerLabel="{answerLabel}" /></td>
	 * <td><obs conceptId="1499"/></td>
	 * <td><obs conceptId="1500"/></td>
	 * <td><obs conceptId="1568" answerConceptIds="1746,843,1743" answerLabels=
	 * "gueri,echec,abandonne"/></td>
	 * </tr>
	 * </obsgroup> </template>
	 * <render conceptId="2125" answerLabel="Traitement initial: 2 HRZE/4 HR"/>
	 * <render conceptId="2125" answerLabel="Traitement initial: 2 HRE/6HE (MSPP)"/>
	 * <render conceptId="2126" answerLabel="Retraitement: 2 SHREZ + 1 HREX + 5 HRE"/>
	 * <render conceptId="2124" answerLabel="Traitement des enfants de &lt; ans: 2 HRZ/4 HR"/>
	 * </repeat> </htmlform> }
	 **/
	private String applyRepeatTemplateTags(String xml) throws Exception {
		Document doc = Htmlform2MuzimaTransformUtil.stringToDocument(xml);
		Node content = Htmlform2MuzimaTransformUtil.findChild(doc, "htmlform");
		
		// We are doing this as follows since I can't seem to get the XML node cloning to work right.
		// We can refactor later as needed if we can get it to work properly, or replace the xml library
		// First we need to parse the document to get the node attributes for repeating elements
		List<List<Map<String, String>>> renderMaps = new ArrayList<List<Map<String, String>>>();
		
		loadRenderElementsForEachRepeatElement(content, renderMaps);
		
		// Now we are just going to use String replacements to explode the repeat tags properly
		Iterator<List<Map<String, String>>> renderMapIter = renderMaps.iterator();
		int idLabelpairIndex = 0;
		while (xml.contains("<repeat>")) {
			int startIndex = xml.indexOf("<repeat>");
			int endIndex = xml.indexOf("</repeat>", startIndex) + 9;
			String xmlToReplace = xml.substring(startIndex, endIndex);
			
			String template = xmlToReplace.substring(xmlToReplace.indexOf("<template>") + 10,
			    xmlToReplace.indexOf("</template>"));
			StringBuilder replacement = new StringBuilder();
			for (Map<String, String> replacements : renderMapIter.next()) {
				String curr = template;
				for (String key : replacements.keySet()) {
					curr = curr.replace("{" + key + "}", replacements.get(key));
				}
				replacement.append(curr);
			}
			xml = xml.substring(0, startIndex) + replacement + xml.substring(endIndex);
			
		}
		
		return xml;
	}
	
	private void loadRenderElementsForEachRepeatElement(Node node, List<List<Map<String, String>>> renderMaps)
	        throws Exception {
		
		NodeList list = node.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			if (n.getNodeName().equalsIgnoreCase("repeat") && !n.hasAttributes()) {
				Node templateNode = Htmlform2MuzimaTransformUtil.findChild(n, "template");
				if (templateNode == null) {
					throw new IllegalArgumentException("All <repeat> elements must contain a child <template> element.");
				}
				List<Map<String, String>> l = new ArrayList<Map<String, String>>();
				NodeList repeatNodes = n.getChildNodes();
				for (int j = 0; j < repeatNodes.getLength(); j++) {
					Node renderNode = repeatNodes.item(j);
					if (renderNode.getNodeName().equalsIgnoreCase("render")) {
						l.add(Htmlform2MuzimaTransformUtil.getNodeAttributes(renderNode));
					}
				}
				renderMaps.add(l);
			} else {
				loadRenderElementsForEachRepeatElement(n, renderMaps);
			}
		}
	}
	
	/**
	 * Handle the new, less-verbose version of the <repeat> tag: </pre>
	 * <p/>
	 * Takes an XML string, finds each {@code <repeat with=""></repeat>} sections in it, and applies
	 * those substitutions <pre>
	 * { @code
	 * <repeat with="['664','No Complaints'], ['832','Weight Loss']">
	 *    <obs conceptId="1069" answerConceptId="{0}" answerLabel="{1}" style="checkbox" /><br/>
	 * </repeat>
	 * }
	 * this will be replaced with,
	 * { @code
	 * <obs conceptId="1069" answerConceptId="644" answerLabel="No Complaints" style=
	"checkbox" /><br/>
	 * <obs conceptId="1069" answerConceptId="832" answerLabel="Weight Loss" style=
	"checkbox" /><br/>
	 * }
	 *
	 * </pre>
	 *
	 * @param xml the xml string to process for repeat sections
	 * @return the xml string after repeat substitutions have been made
	 * @throws Exception
	 */
	private String applyRepeatWithTags(String xml) throws Exception {
		
		while (xml.contains("<repeat with=")) {
			
			int startIndex = xml.indexOf("<repeat with=");
			int endIndex = xml.indexOf("</repeat>", startIndex) + 9;
			
			String xmlToReplace = xml.substring(startIndex, endIndex);
			
			int substitutionSetsStartIndex = xmlToReplace.indexOf("with=") + 6;
			int substitutionSetsEndIndex = xmlToReplace.indexOf("]\">") + 1;
			List<List<String>> substitutionSets = getSubstitutionSets(
			    xmlToReplace.substring(substitutionSetsStartIndex, substitutionSetsEndIndex));
			
			int templateStartIndex = xmlToReplace.indexOf("]\">") + 3;
			int templateEndIndex = xmlToReplace.indexOf("</repeat>");
			String template = xmlToReplace.substring(templateStartIndex, templateEndIndex);
			
			StringBuilder sb = new StringBuilder();
			
			String current = template;
			for (List<String> substitutionSet : substitutionSets) {
				
				int i = 0;
				
				for (String substitution : substitutionSet) {
					current = current.replace("{" + i + "}", substitution);
					i++;
				}
				
				sb.append(current);
				current = template;
			}
			
			xml = xml.substring(0, startIndex) + sb + xml.substring(endIndex);
		}
		
		return xml;
	}
	
	/**
	 * Gets a string like [664,'No Complaints'], [832,'Weight Loss'] and splits it into separate
	 * string entries to be used in the repeated html elements
	 * 
	 * @param val = the string to process and get the entires
	 * @return List of entries
	 */
	private List<List<String>> getSubstitutionSets(String val) {
		
		List<List<String>> substitutionSet = new ArrayList<List<String>>();
		
		// first, strip off the leading and trailing brackets
		val = val.replaceFirst("\\s*\\[\\s*", "");
		val = val.replaceFirst("\\s*\\]\\s*$", "");
		
		// split on " ] , [ "
		for (String subVal : val.split("\\s*\\]\\s*\\,\\s*\\[\\s*")) {
			
			List<String> set = new ArrayList<String>();
			
			// trim off the leading quote and trailing quote
			subVal = subVal.replaceFirst("\\s*\\'", "");
			subVal = subVal.replaceFirst("\\s*'\\s*$", "");
			
			// split on " ',' "
			for (String str : subVal.split("\\s*\\'\\s*\\,\\s*\\'\\s*")) {
				set.add(str);
			}
			
			substitutionSet.add(set);
		}
		
		return substitutionSet;
	}
	
	/**
	 * Applies all the HTML Form Entry tags in a specific XML file (excluding
	 * {@code <macro>, <translations>, and <repeat>)}, by calling the appropriate tag handler (see
	 * {@see org.openmrs.module.htmlformentry.handler}) for each tag
	 * <p/>
	 *
	 * @param xml the xml string to process
	 * @return the xml string (which should now be html with javascript) after tag processing
	 * @throws Exception
	 */
	public String applyTags(String xml, String formName) throws Exception {
		String formId = Htmlform2MuzimaTransformUtil.addUnderScoreBetweenWord(formName);
		Document doc = Htmlform2MuzimaTransformUtil.stringToDocument(xml);
		Node content = Htmlform2MuzimaTransformUtil.findChild(doc, "htmlform");
		StringWriter outHtmlStringWriter = new StringWriter();
		StringWriter outJsStringWriter = new StringWriter();
		outHtmlStringWriter
		        .write("<html>\r\n" + "<head>\r\n" + "\r\n"
		                + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"> \r\n <link href=\"css/bootstrap.min.css\" rel=\"stylesheet\">\r\n"
		                + "    <link href=\"css/muzima.css\" rel=\"stylesheet\">   \r\n"
		                + "    <link href=\"css/bootstrap-datetimepicker.min.css\" rel=\"stylesheet\">\r\n"
		                + "    <link href=\"css/ui-darkness/jquery-ui-1.10.4.custom.min.css\" rel=\"stylesheet\">\r\n"
		                + "    <script src=\"js/jquery.min.js\"></script>\r\n"
		                + "    <script src=\"js/jquery-ui-1.10.4.custom.min.js\"></script>\r\n"
		                + "    <script src=\"js/jquery.validate.min.js\"></script>\r\n"
		                + "    <script src=\"js/additional-methods.min.js\"></script>\r\n"
		                + "    <script src=\"js/muzima.js\"></script>\r\n"
		                + "    <script src=\"js/bootstrap-datetimepicker.min.js\"></script>\r\n"
		                + "    <title>" + formName + "</title>\r\n" + "</head>\r\n"
		                + "<body class=\"col-md-8 col-md-offset-2\">\r\n" + "<div id=\"pre_populate_data\"></div>\r\n"
		                + "<form id=\"" + formId + "\" name=\"" + formId + "\"> \r\n"
		                + "<div class=\"section\">\r\n" + "    <h3>Demographics</h3>\r\n"
		                + "    <div class=\"form-group\">\r\n"
		                + "        <input class=\"form-control\" id=\"patient.uuid\"\r\n"
		                + "               name=\"patient.uuid\" type=\"hidden\" readonly=\"readonly\">\r\n"
		                + "    </div>\r\n" + "    <div class=\"form-group\">\r\n"
		                + "        <label for=\"patient.medical_record_number\">Medical record number:</label>\r\n"
		                + "        <input class=\"form-control\" id=\"patient.medical_record_number\"\r\n"
		                + "               name=\"patient.medical_record_number\" type=\"text\" readonly=\"readonly\">\r\n"
		                + "    </div>\r\n" + "    <div class=\"form-group\">\r\n"
		                + "        <label for=\"patient.family_name\">Family Name:</label>\r\n"
		                + "        <input class=\"form-control\" id=\"patient.family_name\" name=\"patient.family_name\" type=\"text\"\r\n"
		                + "               readonly=\"readonly\">\r\n" + "    </div>\r\n"
		                + "    <div class=\"form-group\">\r\n"
		                + "        <label for=\"patient.given_name\">Given Name:</label>\r\n"
		                + "        <input class=\"form-control\" id=\"patient.given_name\" name=\"patient.given_name\" type=\"text\"\r\n"
		                + "               readonly=\"readonly\">\r\n" + "    </div>\r\n"
		                + "    <div class=\"form-group\">\r\n"
		                + "        <label for=\"patient.middle_name\">Middle Name:</label>\r\n"
		                + "        <input class=\"form-control\" id=\"patient.middle_name\" name=\"patient.middle_name\" type=\"text\"\r\n"
		                + "               readonly=\"readonly\">\r\n" + "    </div>\r\n"
		                + "    <div class=\"form-group\">\r\n" + "        <label for=\"patient.sex\">Gender:</label>\r\n"
		                + "        <select class=\"form-control\" id=\"patient.sex\" name=\"patient.sex\" disabled=\"disabled\">\r\n"
		                + "            <option value=\"\">...</option>\r\n"
		                + "            <option value=\"M\">Male</option>\r\n"
		                + "            <option value=\"F\">Female</option>\r\n" + "        </select>\r\n" + "    </div>\r\n"
		                + "    <div class=\"form-group\">\r\n"
		                + "        <label for=\"patient.birth_date\">Date Of Birth:</label>\r\n"
		                + "        <input class=\"form-control\" id=\"patient.birth_date\" name=\"patient.birth_date\" type=\"text\"\r\n"
		                + "               readonly=\"readonly\" value=\"\">\r\n" + "    </div>\r\n" + "</div>\r\n"
		                + "<div class=\"section\">\r\n" + "    <h3>Encounter Details</h3>");
		
		outJsStringWriter.write("\r\n <script type=\"text/javascript\">\r\n" + "$(document).ready(function () {\r\n"
		        + "    document.setupAutoCompleteDataForProvider('encounter\\\\.provider_id_select');\r\n"
		        + "    document.setupAutoCompleteData('encounter\\\\.location_id');\r\n"
		        + "    document.setupValidationForProvider($('#encounter\\\\.provider_id_select').val(),$(\"#encounter\\\\.provider_id\"));\r\n"
		        + "    document.setupValidationForLocation($('#encounter\\\\.location_id').val(),$(\"encounter\\\\.location_id\"));\r\n"
		        + "$('#save_draft').click(function () {\r\n" + "        $(this).prop('disabled', true);\r\n"
		        + "        document.saveDraft(this);\r\n" + "        $(this).prop('disabled', false);\r\n" + "    });\r\n"
		        + "\r\n" + "    $('#submit_form').click(function () {\r\n" + "        $(this).prop('disabled', true);\r\n"
		        + "        document.submit();\r\n" + "        $(this).prop('disabled', false);\r\n" + "    });"
		        + "\r\n" + "\r\n" + "    const formId = `#" + formId + "`;");
		
		applyTagsHelper(new PrintWriter(outHtmlStringWriter), new PrintWriter(outJsStringWriter), null, content, null);
		outHtmlStringWriter.write("</div>\r\n</form>\r\n" + "</body>");
		outJsStringWriter.write("\r\n" + "});\r\n" + "</script> \r\n </html>");
		outHtmlStringWriter.write(outJsStringWriter.toString());
		
		return outHtmlStringWriter.toString();
	}
	
	private void applyTagsHelper(PrintWriter outHtmlPrintWriter, PrintWriter outJsPrintWriter, Node parent, Node node,
	        Map<String, TagHandler> tagHandlerCache) {
		if (tagHandlerCache == null)
			tagHandlerCache = new HashMap<String, TagHandler>();
		TagHandler handler = null;
		// Find the handler for this node
		{
			String name = node.getNodeName();
			if (name != null) {
				if (tagHandlerCache.containsKey(name)) {
					// we've looked this up before (though it could be null)
					handler = tagHandlerCache.get(name);
				} else {
					handler = Htmlform2MuzimaTransformUtil.getMuzimaFormService().getHtmlformTagHandlerByTagName(name);
					tagHandlerCache.put(name, handler);
				}
			}
		}
		
		if (handler == null)
			handler = this; // do default actions
			
		boolean handleContents = handler.doStartTag(outHtmlPrintWriter, outJsPrintWriter, parent, node);
		
		// Unless the handler told us to skip them, then iterate over any children
		if (handleContents) {
			if (handler != null && handler instanceof IteratingTagHandler) {
				// recurse as many times as the tag wants
				IteratingTagHandler iteratingHandler = (IteratingTagHandler) handler;
				while (iteratingHandler.shouldRunAgain(outHtmlPrintWriter, outJsPrintWriter, parent, node)) {
					NodeList list = node.getChildNodes();
					for (int i = 0; i < list.getLength(); ++i) {
						applyTagsHelper(outHtmlPrintWriter, outJsPrintWriter, node, list.item(i), tagHandlerCache);
					}
				}
				
			} else { // recurse to contents once
				NodeList list = node.getChildNodes();
				for (int i = 0; i < list.getLength(); ++i) {
					applyTagsHelper(outHtmlPrintWriter, outJsPrintWriter, node, list.item(i), tagHandlerCache);
				}
			}
		}
		
		//	<obsgroup> tag is translated to <div> with appropriate attributes, but the closing of the </div> cannot be done in the ObsgroupTagHandler because <obsgroup> 
		//usually have children nodes whose tags must be processed before adding the closing </div>, hence this is handled below
		
		if (node.getNodeName() == "obsgroup") {
			outHtmlPrintWriter.write("</div>");
		}
	}
	
	/**
	 * Provides default getAttributeDescriptors handling (returns null)
	 */
	@Override
	public List<AttributeDescriptor> getAttributeDescriptors() {
		return null;
	}
	
	/**
	 * Provides default start tag handling for tags with no custom handler
	 * <p/>
	 * Default behavior is simply to leave the tag unprocessed. That is, any basic HTML tags are
	 * left as is.
	 *
	 * @should close br tags
	 */
	@Override
	public boolean doStartTag(PrintWriter outHtmlPrintWriter, PrintWriter outJsPrintWriter, Node parent, Node node) {
		if (node.getNodeType() == Node.TEXT_NODE) {
			//do nothing
			//outHtmlPrintWriter.print(node.getNodeValue());
		} else if (node.getNodeType() == Node.COMMENT_NODE) {
			// do nothing
		} else {
			outHtmlPrintWriter.print("<");
			outHtmlPrintWriter.print(node.getNodeName());
			NamedNodeMap attrs = node.getAttributes();
			if (attrs != null) {
				for (int i = 0; i < attrs.getLength(); ++i) {
					Node attr = attrs.item(i);
					outHtmlPrintWriter.print(" ");
					outHtmlPrintWriter.print(attr.getNodeName());
					outHtmlPrintWriter.print("=\"");
					outHtmlPrintWriter.print(attr.getNodeValue());
					outHtmlPrintWriter.print("\"");
				}
			}
			// added so that a single <br/> tag isn't rendered as two line breaks: see HTML-342
			if ("br".equalsIgnoreCase(node.getNodeName())) {
				outHtmlPrintWriter.print("/>");
			} else {
				outHtmlPrintWriter.print(">");
			}
		}
		//replaces htmlformentry's doend tag
		if (!"br".equalsIgnoreCase(node.getNodeName())) {
			outHtmlPrintWriter.print("</" + node.getNodeName() + ">");
		}
		return true;
	}
	
	/**
	 * Takes an xml string, searches for ascii values in the string and replaces them with the
	 * appropriate special characters
	 *
	 * @param xml input string
	 * @return the xml after replacing ascii code with their special characters
	 * @throws Exception
	 * @should return correct xml after replacing ascii code with their special characters
	 */
	public String substituteAsciiCodesWithCharacterCodes(String xml) {
		HashMap<String, String> encodings = new HashMap<String, String>();
		encodings.put("&#160;", "&nbsp;");
		for (String key : encodings.keySet()) {
			Pattern pattern = Pattern.compile(key);
			Matcher matcher = pattern.matcher(xml);
			xml = matcher.replaceAll(encodings.get(key));
		}
		return xml;
	}
	
	/**
	 * Removes htmlform tag and wraps the form in the div tag.
	 *
	 * @param xml
	 * @return xml
	 * @should remove htmlform tag and wrap form in div
	 */
	public String cleanHtml(String xml) {
		xml = xml.trim();
		xml = xml.replaceAll("(?s)<htmlform>(.*)</htmlform>", "$1");
		xml = xml.replaceAll("(?s)</#text>|(?s)</#comment>", "");
		return xml;
	}
	
}

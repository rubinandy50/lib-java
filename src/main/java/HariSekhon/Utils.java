//
//  Author: Hari Sekhon
//  Date: 2015-05-30 12:53:28 +0100 (Sat, 30 May 2015)
//
//  vim:ts=4:sts=4:sw=4:noet
//
//  https://github.com/harisekhon/lib-java
//
//  License: see accompanying Hari Sekhon LICENSE file
//
//  If you're using my code you're welcome to connect with me on LinkedIn and optionally send me feedback to help improve or steer this or other code I publish
//
//  http://www.linkedin.com/in/harisekhon
//

// Port of my personal libraries from other languages I've been using for several years

package HariSekhon;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.lang.Math.pow;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
// 1.3+ API causes problems with Spark, use older API for commons-cli
//import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
//import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

// TODO: if progname check_* stderr to stdout and trap exit codes to 2
// TODO: predefined options
// TODO: env support
// TODO: timeout support
// TODO: autoflush
// TODO: threshold range support
// TODO: cmd
// TODO: curl and curl_json
// TODO: get_field
// TODO: get_path_owner
// TODO: go_flock_yourself / flock_off

// Methods should not allow unhandled Exceptions since we want to catch and provide concise one liner errors

public class Utils {
	
    private static final String utils_version = "1.13.1";
	public static boolean stdout = false;
	public static Options options = new Options();
	
	public static String msg = "";
	public static final String nagios_plugins_support_msg = "Please try latest version from https://github.com/harisekhon/nagios-plugins, re-run on command line with -vvv and if problem persists paste full output from -vvv mode in to a ticket requesting a fix/update at https://github.com/harisekhon/nagios-plugins/issues/new";
    public static final String nagios_plugins_support_msg_api = "API may have changed. " + nagios_plugins_support_msg;
	
    private static final HashMap<String, Integer> exit_codes = new HashMap<String, Integer>();
    private static String status = "UNKNOWN";
	private static int verbose = 0;
	private static int timeout = -1;
	private static boolean debug = false;
	
	// keeping this lowercase to make it easier to do String.toLowerCase() case insensitive matches
	private static final ArrayList<String> valid_units = new ArrayList<String>(Arrays.asList(
	                                                                                            "%",
	                                                                                            "s",
	                                                                                            "ms",
	                                                                                            "us",
	                                                                                            "b",
	                                                                                            "kb",
	                                                                                            "mb",
	                                                                                            "gb",
	                                                                                            "tb",
	                                                                                            "c"
	));
	
	static {
		// java autoboxing
		exit_codes.put("OK", 		0);
		exit_codes.put("WARNING", 	1);
		exit_codes.put("CRITICAL", 	2);
		exit_codes.put("UNKNOWN", 	3);
		exit_codes.put("DEPENDENT", 4);
		
		// 1.3+ API doesn't work in Spark which embeds older commons-cli
		//options.addOption(Option.builder("t").longOpt("timeout").argName("secs").required(false).desc("Timeout for program (Optional)").build());
		// .create() must come last as it generates Option on which we cannot add long opt etc
		//options.addOption(OptionBuilder.create("t").withLongOpt("timeout").withArgName("secs").withDescription("Timeout for program (Optional)").create("t"));
		options.addOption(OptionBuilder.withLongOpt("timeout").withArgName("secs").hasArg().withDescription("Timeout for program (Optional)").create("t"));
		options.addOption(OptionBuilder.withLongOpt("debug").withDescription("Debug mode (Optional)").create("D"));
		options.addOption("v", "verbose", false, "Verbose mode");
		options.addOption("h", "help", false, "Print usage help and exit");
		//CommandLine cmd = get_options(new String[]{"test", "test2"});
	}

	// ===================================================================== //
	// 
	//                S t a t u s   h e l p e r   m e t h o d s
	//
	// ===================================================================== //
	
	
	public static final String getStatus(){
		return status;
	}
	
	public static final int getStatusCode(){
		return exit_codes.get(status);
	}
		
	public static final void setStatus (String key) {
		if(exit_codes.containsKey(key)){
			status = key;
		} else {
			arg_error("invalid status '" + key + "' passed to setStatus(), must be one of: " + exit_codes.keySet().toString());
		}
	}
	
	public static final void unknown(){
		if(getStatus() == null || getStatus().equals("OK")){
			setStatus("UNKNOWN");
		}
	}
	
	public static final void warning(){
		if(getStatus() == null || ! getStatus().equals("CRITICAL")){
			setStatus("WARNING");
		}
	}
	
	public static final void critical(){
		setStatus("CRITICAL");
	}
	
	public static final Boolean is_ok(){
	    if(getStatus() == null){
	        return false;
	    }
		return getStatus().equals("OK");
	}
	
	
	public static final Boolean is_warning(){
	    if(getStatus() == null){
	        return false;
	    }
		return getStatus().equals("WARNING");
	}
	
	public static final Boolean is_critical(){
	    if(getStatus() == null){
	        return false;
	    }
		return getStatus().equals("CRITICAL");
	}
	
	public static final Boolean is_unknown(){
	    if(getStatus() == null){
	        return false;
	    }
		return getStatus().equals("UNKNOWN");
	}

	
	public static final int getStatusCode (String key) {
		if(key != null && exit_codes.containsKey(key)){
			return exit_codes.get(key);
		} else {
			arg_error("invalid status '" + key + "' passed to getStatusCode()");
			return exit_codes.get("UNKNOWN"); // appease Java return, won't get called
		}
	}

	
	public static final void status(){
		vlog("status: " + getStatus());
	}
	
	public static final void status2(){
		vlog2("status: " + getStatus());
	}
	
	public static final void status3(){
		vlog3("status: " + getStatus());
	}

	
	// ===================================================================== //
	//
	//                             R e g e x
	//
	// ===================================================================== //
	
	// neither Google's com.Guava google.common.net.HostSpecifier nor Apache Commons org.apache.commons.validator.routines.DomainValidator are suitable for my needs here, must port the more flexible regex methods from my Perl library
	
	// years and years of Regex expertise and testing has gone in to this, do not edit!
	// This also gives flexibility to work around some situations where domain names may not be quite valid (eg .local/.intranet) but still keep things quite tight
	// There are certain scenarios where Google Guava and Apache Commons libraries don't help with these
	// AWS regex from http://blogs.aws.amazon.com/security/blog/tag/key+rotation
	public static final String aws_access_key_regex     = "(?<![A-Z0-9])[A-Z0-9]{20}(?![A-Z0-9])";
	public static final String aws_host_component       = "ip-(?:10-\\d+-\\d+-\\d+|172-1[6-9]-\\d+-\\d+|172-2[0-9]-\\d+-\\d+|172-3[0-1]-\\d+-\\d+|192-168-\\d+-\\d+)";
    public static final String aws_secret_key_regex     = "(?<![A-Za-z0-9/+=])[A-Za-z0-9/+=]{40}(?![A-Za-z0-9/+=])";
	public static final String ip_prefix_regex 			= "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}";
	// now allowing 0 or 255 as the final octet due to CIDR
	public static final String ip_regex 				= ip_prefix_regex + "(?:25[0-5]|2[0-4][0-9]|[01]?[1-9][0-9]|[01]?0[1-9]|[12]00|[0-9])\\b";
	public static final String hostname_component_regex = "\\b[A-Za-z0-9](?:[A-Za-z0-9_\\-]{0,61}[a-zA-Z0-9])?\\b";
	// TODO: replace this with IANA TLDs as done in my Perl lib
	public static final String tld_regex				= "\\b(?i:[A-Za-z]{2,4}|london|museum|travel|local|localdomain|intra|intranet|internal)\\b";
	public static final String domain_component			= "\\b[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\b";
	public static final String domain_regex				= "(?:" + domain_component + "\\.)*" + tld_regex;
	public static final String domain_regex_strict 		= "(?:" + domain_component + "\\.)+" + tld_regex;
	public static final String hostname_regex			= String.format("%s(?:\\.%s)?", hostname_component_regex, domain_regex);
	public static final String aws_hostname_regex       = aws_host_component + "(?:\\." + domain_regex + ")?";
	public static final String host_regex 	  			= String.format("\\b(?:%s|%s)\\b", hostname_regex, ip_regex);
	public static final String dirname_regex 			= "[/\\w\\s\\\\.:,*()=%?+-]+";
	public static final String filename_regex 			= dirname_regex + "[^/]";
	public static final String rwxt_regex 	  	 		= "[r-][w-][x-][r-][w-][x-][r-][w-][xt-]";
	public static final String fqdn_regex 	  	 		= hostname_component_regex + "\\." + domain_regex;
	public static final String aws_fqdn_regex           = aws_host_component + "\\." + domain_regex;
	public static final String email_regex 	  	 		= "\\b[A-Za-z0-9](?:[A-Za-z0-9\\._\\%\\'\\+-]{0,62}[A-Za-z0-9\\._\\%\\+-])?@" + domain_regex + "\\b";
	public static final String subnet_mask_regex 		= "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[1-9][0-9]|[01]?0[1-9]|[12]00|[0-9])\\b";
	public static final String mac_regex				= "\\b[0-9A-F-af]{1,2}[:-](?:[0-9A-Fa-f]{1,2}[:-]){4}[0-9A-Fa-f]{1,2}\\b";
	public static final String process_name_regex		= "[\\w\\s./<>-]+";
	public static final String url_path_suffix_regex	= "/(?:[\\w.,:/%&?!=*|\\[\\]~+-]+)?"; // there is an RFC3987 regex but it's gigantic, this is easier to reason about and serves my needs
	public static final String url_regex				= "\\b(?i:https?://)?" + host_regex + "(?::\\d{1,5})?(?:" + url_path_suffix_regex + ")?";
	public static final String user_regex				= "\\b[A-Za-z][A-Za-z0-9_-]*[A-Za-z0-9]\\b";
	public static final String column_regex				= "\\b[\\w:]+\\b";
	public static final String ldap_dn_regex			= "\\b\\w+=[\\w\\s]+(?:,\\w+=[\\w\\s]+)*\\b";
	public static final String krb5_principal_regex		= String.format("%s(?:/%s)?(?:@%s)?", user_regex, hostname_regex, domain_regex);
	public static final String threshold_range_regex	= "^(@)?(-?\\d+(?:\\.\\d+)?)(:)(-?\\d+(?:\\.\\d+)?)?$";
	public static final String threshold_simple_regex	= "^(-?\\d+(?:\\.\\d+)?)$";
	public static final String version_regex 			= "\\d(\\.\\d+)*";
	
	// ===================================================================== //
	
	public static final Boolean check_regex (String string, String regex) {
		if(string == null){
		    arg_error("undefined string passed to check_regex()");
		}
		if(regex == null){
		    arg_error("undefined regex passed to check_regex()");
		}
		if(! isRegex(regex)){
		    arg_error("invalid regex passed to check_regex()");
		}
		if(string.matches(regex)){
		    return true;
		}
		critical();
	    return false;
	}
	
	
	public static final Boolean check_string (String str, String expected, Boolean no_msg) {
		if(expected == null){
			arg_error("passed null as expected string to check_string()");
		}
		if(str != null && ( str == expected || str.equals(expected) ) ){
		    return true;
		}
	    critical();
	    /* implement when msg and thresholds are done
	    if(nomsg){
	    	
	    }
	    */
	    return false;
	}
	public static final Boolean check_string (String str, String expected) {
		return check_string(str, expected, false);
	}

	
	public static final double expand_units (double num, String units, String name) {
	    name = name(name);
	    if(units == null){
	        arg_error("null passed for units to expand_units()");
	    }
	    name = name.trim();
	    units = units.trim();
	    if(! name.isEmpty()){
	        name = " for " + name;
	    }
		int power = 1;
		if(units.matches("(?i)^B?$")){
			return num;
		} else if(units.matches("(?i)^KB?$")){ power = 1;
		} else if(units.matches("(?i)^MB?$")){ power = 2;
		} else if(units.matches("(?i)^GB?$")){ power = 3;
		} else if(units.matches("(?i)^TB?$")){ power = 4;
		} else if(units.matches("(?i)^PB?$")){ power = 5; 
		} else {
			arg_error(String.format("unrecognized units '%s' passed to expand_units()%s", units, name));
		}
		return (num * ( pow(1024, power) ) );
	}
	public static final double expand_units (double num, String units) {
		return expand_units(num, units, null);
	}
	public static final long expand_units (long num, String units, String name) {
		return (long) expand_units((double)num, units, name);
	}
	public static final long expand_units (long num, String units) {
		return expand_units(num, units, null);
	}

	
	public static final void hr(){
		println("# " + StringUtils.repeat("=", 76) + " #");
	}
	
	
	public static final String human_units (double num, String units, Boolean terse) {
	    if(units == null){
	    	units = "";
	    }
	    units = units.trim();
		if(!units.isEmpty()){
			num = expand_units(num, units, "human_units");
		}
		// TODO: remove trailing zeros .00 from doubles
		if(num >= pow(1024, 7)){
			arg_error(String.format("determined suspicious units for number '%s', larger than Exabytes?!!", num));
		} else if(num >= pow(1024, 6)){
			//num_str = String.format("%.2f", num / pow(1024, 6)).replaceFirst("\\.0+$", "");
			num = num / pow(1024, 6);
			units = "EB";
		} else if(num >= pow(1024, 5)){
			//num_str = String.format("%.2f", num / pow(1024, 5));
			num = num / pow(1024, 5);
			units = "PB";
		} else if(num >= pow(1024, 4)){
			//num_str = String.format("%.2f", num / pow(1024, 4));
			num = num / pow(1024, 4);
			units = "TB";
		} else if(num >= pow(1024, 3)){
			//num_str = String.format("%.2f", num / pow(1024, 3));
			num = num / pow(1024, 3);
			units = "GB";
		} else if(num >= pow(1024, 2)){
			//num_str = String.format("%.2f", num / pow(1024, 2));
			num = num / pow(1024, 2);
			units = "MB";
		} else if(num >= pow(1024, 1)){
			//num_str = String.format("%.2f", num / pow(1024, 1));
			num = num / pow(1024, 1);
			units = "KB";
		} else if(num < 1024){
			//num_str = String.valueOf(num);
			if(terse){
				//return String.format("%sB", num);
				units = "B";
			} else {
				//return String.format("%s bytes", num);
				units = " bytes";
			}
		} else {
			arg_error("unable to determine units for number num");
		}
		String num_str = String.format("%.2f", num).replaceFirst("(\\.\\d+)0$", "$1").replaceFirst("\\.0+$", "");
		return num_str + units;
	}
	public static final String human_units (double num, String units) {
		return human_units(num, units, false);
	}
	public static final String human_units (double num) {
		return human_units(num, null, false);
	}
	
	public static final String strip_scheme (String str) {
		if(str.matches("^\\w+://[^/].*")){
			return str.replaceFirst("^\\w+://", "");
		} else {
			return str.replaceFirst("^\\w+:/+", "/");
		}
	}
	
	public static final String strip_scheme_host (String str) {
		if(str.matches("^\\w+:///[^/].*")){
			return str.replaceFirst("^\\w+:///", "/");
		} else {
			//return str.replaceFirst("^\\w+:(?://[^/]+)?/", "/");
			return str.replaceFirst("^\\w+:(?://" + host_regex + "(?::\\d+)?)?/", "/");
		}
	}
	
	
	// ===================================================================== //
	//
	//                 C o n v e r s i o n   U t i l i t i e s
	//
	// ===================================================================== //
	
	public static final ArrayList<String> array_to_arraylist(String[] array) {
	    return new ArrayList<String>(Arrays.asList(array));
	}

	public static final String[] arraylist_to_array (ArrayList<String> arrayList) {
	    String []array = new String[arrayList.size()];
	    return arrayList.toArray(array);
	}
	
	
	// ===================================================================== //
	//
	//                     Non-deterministic Ordering
	//
	public static final String[] uniq_array (String[] list) {
	    HashSet<String> set = new HashSet<String>();
	    for(String item: list) {
	        set.add(item);
	    }
	    String[] a = {};
	    return set.toArray(a);
	}
	
	public static final ArrayList<String> uniq_arraylist (ArrayList<String> list) {
	    HashSet<String> set = new HashSet<String>(list);
	    ArrayList<String> a = new ArrayList<String>();
	    a.addAll(set);
	    return a;
	}

	// ===================================================================== //
	//
	//                     Order Preserving
	//
	public static final String[] uniq_array_ordered (String[] list){
	    Set<String> set = new LinkedHashSet<String>();
	    for(String item: list) {
            set.add(item);
        }
	    String[] a = {};
	    return set.toArray(a);
	}
	
	public static final ArrayList<String> uniq_arraylist_ordered (ArrayList<String> list) {
	    Set<String> set = new LinkedHashSet<String>(list);
	    ArrayList<String> a = new ArrayList<String>();
	    a.addAll(set);
	    return a;
	}
	
	
	// ===================================================================== //
	//
	//                          V a l i d a t i o n
	//
	// ===================================================================== //
	
	public static final Boolean isAlNum (String str) {
	    if(str == null){
            return false;
        }
        return str.matches("^[A-za-z0-9]+$");
	}
	
	
	public static final Boolean isAwsAccessKey (String str) {
	    if(str == null){
            return false;
        }
        return str.matches("^" + aws_access_key_regex + "$");
	}
	
	public static final Boolean isAwsHostname (String str) {
	    if(str == null){
	        return false;
	    }
	    return str.matches("^" + aws_hostname_regex + "$");
	}
	
	public static final Boolean isAwsFqdn (String str) {
	    if(str == null){
	        return false;
	    }
	    return str.matches("^" + aws_fqdn_regex + "$");
	}
	
	
	public static final Boolean isAwsSecretKey (String str) {
	    if(str == null){
            return false;
        }
		return str.matches("^" + aws_secret_key_regex + "$");
	}
	
	
	public static final Boolean isChars (String str, String char_range) {
	    if(str == null || char_range == null){
            return false;
        }
        if(!isRegex(String.format("[%s]", char_range))){
			arg_error("invalid regex char range passed to isChars()");
		}
		return str.matches(String.format("^[%s]+$", char_range));
	}
	
	
	public static final Boolean isCollection (String collection) {
	    if(collection == null || collection.trim().isEmpty()){
	        return false;
	    }
	    return collection.matches("^(\\w(?:[\\w.]*\\w)?)$");
	}
	
	
	public static final Boolean isDatabaseName (String database) {
	    if(database == null || database.trim().isEmpty()){
	        return false;
	    }
	    return database.matches("^\\w+$");
	}
	
	public static final Boolean isDatabaseColumnName (String column) {
	    if(column == null || column.trim().isEmpty()){
            return false;
        }
        return column.matches("^" + column_regex + "$");
	}
	
	
	public static final Boolean isDatabaseFieldName (String field) {
	    if(field == null || field.trim().isEmpty()){
            return false;
        }
	    // allows field number integer or field name
        return field.matches("^(?:\\d+|[\\w()*,._-]+)$");
	}
	
	
	public static final Boolean isDatabaseTableName (String table, Boolean allow_qualified) {
	    if(table == null || table.trim().isEmpty()){
	        return false;
	    }
		if(allow_qualified){
			return table.matches("^[A-Za-z0-9][\\w.]*[A-Za-z0-9]$");
		} else {
			return table.matches("^[A-Za-z0-9]\\w*[A-Za-z0-9]$");
		}
	}
	public static final Boolean isDatabaseTableName (String table) {
	    return isDatabaseTableName(table, false);
	}
	
	
	public static final Boolean isDatabaseViewName (String view, Boolean allow_qualified) {
		return isDatabaseTableName(view, allow_qualified);
	}
	public static final Boolean isDatabaseViewName (String view) {
	    return isDatabaseViewName(view, false);
	}

	
	public static final Boolean isDirname(String dir){
	    if(dir == null || dir.trim().isEmpty()){
	        return false;
	    }
	    return dir.matches("^" + dirname_regex + "$");
	}
	
	public static final Boolean isDomain (String domain) {
	    if(domain == null || domain.trim().isEmpty()){
	        return false;
	    }
		return domain.matches("^" + domain_regex + "$");
	}

	
	public static final Boolean isDomainStrict (String domain) {
	    if(domain == null || domain.trim().isEmpty()){
	        return false;
	    }
		return domain.matches("^" + domain_regex_strict + "$");
	}
	
	
	public static final Boolean isDnsShortName (String dns) {
		if(dns == null || dns.trim().length() < 3 || dns.length() > 63 ){
			return false;
		}
		return dns.matches("^" + hostname_component_regex + "$");
	}
	
	
	public static final Boolean isEmail (String email) {
	    if(email == null || email.trim().isEmpty() || email.length() > 256){
	        return false;
	    }
	    return email.matches("^" + email_regex + "$");
	}
	
	
	public static final Boolean isFilename (String filename) {
	    if(filename == null || filename.trim().isEmpty()){
	        return false;
	    }
	    return filename.matches("^" + filename_regex + "$");
	}
	
	
	public static final Boolean isFqdn (String fqdn) {
	    if(fqdn == null || fqdn.trim().isEmpty() || fqdn.length() > 255){
	        return false;
	    }
	    return fqdn.matches("^" + fqdn_regex + "$");
	}
	
	
	public static final Boolean isHex (String hex) {
	    if(hex != null && hex.matches("^0x[A-Fa-f\\d]+$")){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	// at casual glance this looks like it's duplicating isHostname but it's using a different unified regex of isHostname + isIP
	public static final Boolean isHost (String host) {
	    if(host == null || host.trim().isEmpty()){
	        return null;
	    }
	    if(host.length() > 255){
	    	return false;
	    } else if(host.matches("^" + host_regex + "$")){
	        return true;
	    } else if(isIP(host)){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	
	public static final Boolean isHostname (String hostname) {
	    if(hostname != null && hostname.length() < 256 && hostname.matches("^" + hostname_regex + "$")){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	
	public static final Boolean isInterface (String networkInterface) {
	    if(networkInterface != null && networkInterface.matches("^((?:em|eth|bond|lo)\\d+|lo)$")){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	
	// TODO: isJson
    // TODO: isXML
	
	
	public static final Boolean isKrb5Princ (String princ) {
	    if(princ != null && princ.matches("^" + krb5_principal_regex + "$")){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	
	public static final Boolean isLabel (String label) {
	    if(label != null && label.matches("^[%()/*\\w\\s-]+$")){
	        return true;
	    } else {
	        return false;
	    }
	}

	
	public static final Boolean isLdapDn (String dn) {
	    if(dn != null && dn.matches("^" + ldap_dn_regex + "$")){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	
	public static final Boolean isNagiosUnit (String unit) {
	    if(unit != null && valid_units.contains(unit.toLowerCase())){
	        return true;
	    } else {
	        return false;
	    }
	}
	
	
	public static final Boolean isNoSqlKey (String key) {
	    if(key != null && key.matches("^[\\w_,.:+-]+$")){
	        return true;
	    } else {
	         return false;
	    }
	}
	
	
    public static final Boolean isIP (String ip) {
        if(ip == null || ! ip.matches("^" + ip_regex + "$")){
            return false;
        }
        String[] octets = ip.split("\\.");
        if(octets.length != 4){
            return false;
        }
        for(String octet: octets){
            int octet_int;
            try{
                octet_int = Integer.parseInt(octet);
            } catch (Exception e) {
                return false;
            }
            if(octet_int < 0 || octet_int > 255){
                return false;
            }
        }
        return true;
    }
    
    
    public static final Boolean isPort (String port) {
        if(port == null || ! port.matches("^\\d+$")){
            return false;
        }
        int port_int;
        try{
            port_int = Integer.parseInt(port);
        } catch (Exception e) {
            return false;
        }
        return isPort(port_int);
    }
    
    public static final Boolean isPort (int port_int) {
        if(port_int < 1 || port_int > 65535){
            return false;
        }
        return true;
    }
    
    
    public static final Boolean isProcessName (String proc) {
        if(proc != null && proc.matches("^"+ process_name_regex + "$")){
            return true;
        } else {
            return false;
        }
    }
	
    
    public static final Boolean isRegex (String regex) {
        try {
            "".matches(regex);
        } catch (Exception e){
            return false;
        }
        return true;
    }
    
    
    // not implementing isScientific
    
    // TODO: isThreshold()
    
    
    public static final Boolean isUrl (String url) {
        if(url == null || url.trim().isEmpty()){
            return false;
        }
        if(url.matches("^" + url_regex + "$")){
            return true;
        } else {
            return false;
        }
    }
    
    
    public static final Boolean isUrlPathSuffix (String url) {
        if(url != null && url.matches("^" + url_path_suffix_regex + "$")){
            return true;
        } else {
            return false;
        }
    }
    
    
    public static final Boolean isUser (String user) {
        if(user != null && user.matches("^" + user_regex + "$")){
            return true;
        } else {
            return false;
        }
    }
    
    
    public static final Boolean isVersion (String version) {
        if(version != null && version.matches("^" + version_regex + "$")){
            return true;
        } else {
            return false;
        }
    }
    
    
    // ===================================================================== //
    //
    //                      O S   H e l p e r s
    //
    // ===================================================================== //
    
    /*
    public static final void print_java_properties(){
        System.getProperties().list(System.out);
    }
    */
    
    public static final String getOS(){
    	String os = System.getProperty("os.name");
    	if(os == null){
    		throw new IllegalStateException("unknown OS, retrieved null for OS");
    	}
		if(os.trim().isEmpty()){
    		throw new IllegalStateException("unknown OS, retrieved blank for OS");
    	}
    	return os;
    }
    
    public static final Boolean isOS (String os) {
        if(os != null && os.equals(getOS())){
            return true;
        } else {
            return false;
        }
    }
    
    public static final Boolean isMac(){
        return isOS("Mac OS X");
    }
    
    public static final Boolean isLinux(){
        return isOS("Linux");
    }
    
    public static final Boolean isLinuxOrMac(){
    	return isLinux() || isMac();
    }
    
    public static final String supported_os_msg = "this program is only supported on %s at this time";
 
    // TODO: change these to raise UnsupportedOperatingSystemException + then unit test
    public static final void linux_only(){
        if( ! isLinux() ){
            quit("UNKNOWN", String.format(supported_os_msg, "Linux"));
        }
    }
    
    public static final void mac_only(){
        if( ! isMac() ){
            quit("UNKNOWN", String.format(supported_os_msg, "Mac OS X"));
        }
    }
    
    public static final void linux_mac_only(){
        if( ! ( isLinux() || isMac() ) ){
            quit("UNKNOWN", String.format(supported_os_msg, "Linux or Mac OS X"));
        }
    }
    
    
    // ===================================================================== //

    // minimum_value - use Math.min

    // TODO: msg_perf_thresholds()
    // TODO: msg_thresholds()
    
    /*
    public static final int month2int (String month) {
        if(month == null){
            arg_error("null passed to month2int");
        }
        HashMap<String, Integer> month_map = new HashMap<String, Integer>() {{
            put("jan", 1);
            put("feb", 2);
            put("mar", 3);
            put("apr", 4);
            put("may", 5);
            put("jun", 6);
            put("jul", 7);
            put("aug", 8);
            put("sep", 9);
            put("oct", 10);
            put("nov", 11);
            put("dec", 12);
        }};
        if(month_map.containsKey(month.toLowerCase())){
            return month_map.get(month.toLowerCase());
        } else {
            arg_error("invalid month passed to month2int()");
        }
        return -1; // purely to appease Java - won't reach here
    }
    */
    
    // TODO:
    // open_file
    // parse_file_option
    // plural
    // prompt
    // isYes
    // random_alnum
    // sec2min
    // sec2human
    // set_sudo
    // set_timeout
    
    
    public static final String resolve_ip (String host) {
        if(host == null || host.trim().isEmpty()){
            arg_error("no host passed to resolve_ip");
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }
    
    // only works on unix systems
    public static final Boolean user_exists (String user){
    	linux_mac_only();
        if(user == null || user.trim().isEmpty()){
            return false;
        }
        if(! isUser(user)){
            return false;
        }
        StringBuilder id = new StringBuilder();
        try {
            Process child = Runtime.getRuntime().exec("id -u " + user);
            InputStream in = child.getInputStream();
            int c;
            while((c = in.read()) != -1){
                id.append((char)c);
            }
            in.close();
        } catch (Exception e){
            return false;
        }
        String id2 = id.toString();
        if(id2 != null){
            return true;
        }
        return false;
    }
    
    // TODO: show last caller in arg_error
    public static final void arg_error (String msg) {
        //println("CODE ERROR: " + msg);
        //System.exit(exit_codes.get("UNKNOWN"));
        throw new IllegalArgumentException(msg);
    }
    
    public static final void state_error (String msg) {
        //println("CODE ERROR: " + msg);
        //System.exit(exit_codes.get("UNKNOWN"));
        throw new IllegalStateException(msg);
    }
    
    
    public static final void quit (String status, String msg) {
        println(status + ": " + msg);
        if(exit_codes.containsKey(status)) {
            System.exit(exit_codes.get(status));
        } else {
            // TODO: provide a warning stack trace here
            arg_error(String.format("specified an invalid exit status '%s' to quit()", status));
        }
    }
    
    public static final void quit (String msg){
        println("CRITICAL: " + msg);
        System.exit(exit_codes.get("CRITICAL"));
    }
    
    
	public static final CommandLine get_options (String[] args) throws ParseException {
		// 1.3+ API problem with Spark, go back to older API for commons-cli
		//CommandLineParser parser = new DefaultParser();
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
			if(cmd.hasOption("h")){
				usage();
			}
			if(cmd.hasOption("D")){
				debug = true;
			}
			if(cmd.hasOption("V")){
				verbose += 1;
			}
			if(cmd.hasOption("T")){
				// fix this
				timeout = Integer.valueOf(cmd.getOptionValue("T", "-1"));
			}
		} catch (ParseException e){
			if(debug){
				throw e;
			}
			println(e + "\n");
			usage();
		}
		return cmd;
	}
	
	
	public static final void usage (String msg) {
		if(msg != null){
			println(msg + "\n");
		}
		HelpFormatter formatter = new HelpFormatter();
		// TODO: get caller's class name to populate this
		formatter.printHelp("<className> [options]", options);
		System.exit(exit_codes.get("UNKNOWN"));
	}
	
	public static final void usage(){
		usage(null);
	}
	
	// because System.out.println is still annoying and Scala has a short version
	public static final void println (String msg) {
		//if(stdout){
			System.out.println(msg);
		//} else {
		//	System.err.println(msg);
		//}
	}
	public static final void println (double num) {
		println(String.valueOf(num));
	}
	public static final void println (long num) {
		println(String.valueOf(num));
	}
	public static final void println (Boolean b){
		println(b.toString());
	}
	
	/*
	private static final void printf (String msg, String... args) {
		System.out.printf(String.format("%s", msg), (Object[]) args); // cast to Object[] to avoid warning about String and Object not quite matching up
	}
	*/
	
	
	public static final String repeat_string(String chars, int num) {
	    StringBuilder string = new StringBuilder(); 
	    for(int i = 0; i < num; i++){
	        string.append(chars);
	    }
	    return string.toString();
	}
	
	
	// ===================================================================== //
	//
	//                          O p t i o n s
	//
	// ===================================================================== //
	
	public static final void HostOptions(){
		OptionBuilder.withLongOpt("host");
		OptionBuilder.withArgName("host");
		OptionBuilder.withDescription("Host ($HOST)");
		OptionBuilder.hasArg();
		OptionBuilder.isRequired();
		options.addOption(OptionBuilder.create("H"));
		
		OptionBuilder.withLongOpt("port");
		OptionBuilder.withArgName("port");
		OptionBuilder.withDescription("Port ($PORT)");
		OptionBuilder.hasArg();
		OptionBuilder.isRequired();
		options.addOption(OptionBuilder.create("P"));
	}
	
	// ===================================================================== //
	//
	//                          V a l i d a t i o n
	//
	// ===================================================================== //
	
	// these methods are intentionally not throwing exceptions as they are designed for CLI usage and exit with usage()
	// for try and recover behaviour use the corresponding is* methods which return Boolean
	
	public static final String name (String name) {
	    if(name == null){
	        return "";
	    }
	    name = name.trim();
	    if(! name.isEmpty()){
	        name = name + " ";
	    }
	    return name;
	}
	
	public static final String require_name (String name) {
	    if(name == null || name.trim().isEmpty()){
	        // TODO: improve the feedback location 
	        //StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace()
	        arg_error("name arg not defined when calling method");
	    }
	    return name.trim();
	}
	
	
	public static final String validate_alnum (String arg, String name){
	    name = require_name(name);
	    if(arg == null || arg.trim().isEmpty()){
	        usage(name + "not defined");
	    }
	    arg = arg.trim();
	    if(! arg.matches("^[A-Za-z0-9]+$")){
	        usage("invalid " + name + "defined: must be alphanumeric");
	    }
	    vlog_options(name, arg);
	    return arg;
	}

	
	public static final String validate_chars (String string, String name, String chars) {
	    name = require_name(name);
	    if(chars == null || chars.trim().isEmpty()){
	        arg_error("chars field not defined when calling validate_chars()");
	    }
	    chars = chars.trim();
	    if(string == null || string.isEmpty()){
	        usage(name + "not defined");
	    }
	    if(! isChars(string, chars)){
	        usage("invalid " + name + "defined: must be one of the following chars - " + chars);
	    }
	    vlog_options(name, string);
	    return string;
	}
	
	
	public static final String validate_aws_access_key (String key) {
	    if(key == null || key.trim().isEmpty()){
	        usage("aws access key not defined");
	    }
	    key = key.trim();
	    if(! isAwsAccessKey(key)){
	        usage("invalid aws access key defined: must be 20 alphanumeric chars");
	    }
	    vlog_options("aws access key", repeat_string("X", 18) + key.substring(18, 20));
	    return key;
	}
	
	
	public static final String validate_aws_bucket (String bucket) {
	    if(bucket == null || bucket.trim().isEmpty()){
	        usage("aws bucket not defined");
	    }
	    bucket = bucket.trim();
	    if(! isDnsShortName(bucket)){
	        usage("invalid aws bucket name defined: must be alphanumeric between 3 and 63 characters long");
	    }
	    if(isIP(bucket)){
	        usage("invalid aws bucket name defained: may not be formmatted as an IP address");
	    }
	    vlog_options("aws bucket", bucket);
	    return bucket;
	}
	
	
	public static final String validate_aws_secret_key (String key) {
       if(key == null || key.trim().isEmpty()){
            usage("aws secret key not defined");
        }
        key = key.trim();
        if(! isAwsSecretKey(key)){
            usage("invalid aws secret key defined: must be 20 alphanumeric chars");
        }
        vlog_options("aws secret key", repeat_string("X", 38) + key.substring(38, 40));
        return key;
	}
	
	
	public static final String validate_collection (String collection, String name) {
	    name = name(name);
	    if(collection == null || collection.trim().isEmpty()){
	        usage(name + "collection not defined");
	    }
	    collection = collection.trim();
	    if(! isCollection(collection)){
	        usage("invalid " + name + "collection defined: must be alphanumeric, with optional periods in the middle");
	    }
	    vlog_options(name + "collection", collection);
	    return collection;
	}
	public static final String validate_collection (String collection) {
	    return validate_collection(collection, null);
	}
	
	
	public static final String validate_database (String database, String name) {
	    name = name(name);
	    if(database == null || database.trim().isEmpty()){
	        usage(name + "database not defined");
	    }
	    database.trim();
	    if(! isDatabaseName(database)){
	        usage("invalid " + name + "database defined: must be alphanumeric");
	    }
	    vlog_options(name + "database", database);
	    return database;
	}
	public static final String validate_database (String database) {
	    return validate_database(database, null);
	}
	
	
	public static final String validate_database_tablename (String table, String name, Boolean allow_qualified){
	    name = name(name);
	    if(table == null || table.trim().isEmpty()){
	        usage(name + "table not defined");
	    }
	    table = table.trim();
	    if(! isDatabaseTableName(table, allow_qualified)){
	        usage("invalid " + name + "table defined: must be alphanumeric");
	    }
	    vlog_options(name + "table", table);
	    return table;
	}
	public static final String validate_database_tablename (String table, String name) {
	    return validate_database_tablename(table, name, false);
	}
	public static final String validate_database_tablename (String table, Boolean allow_qualified){
	    return validate_database_tablename(table, null, allow_qualified);
	}
	public static final String validate_database_tablename (String table){
	    return validate_database_tablename(table, null, false);
	}

	
	public static final String validate_database_viewname (String view, String name, Boolean allow_qualified){
	    name = name(name);
	    if(view == null || view.trim().isEmpty()){
	        usage(name + "view not defined");
	    }
	    view = view.trim();
	    if(! isDatabaseViewName(view, allow_qualified)){
	        usage("invalid " + name + "view defined: must be alphanumeric");
	    }
	    vlog_options(name + "view", view);
	    return view;
	}
	public static final String validate_database_viewname (String view, String name) {
	    return validate_database_viewname(view, name, false);
	}
	public static final String validate_database_viewname (String view, Boolean allow_qualified) {
	    return validate_database_viewname(view, null, allow_qualified);
	}
	public static final String validate_database_viewname (String view) {
	    return validate_database_viewname(view, null, false);
	}
	
	
	public static final String validate_database_columnname (String column) {
	    if(column == null || column.trim().isEmpty()){
	        usage("column not defined");
	    }
	    column = column.trim();
	    if(! isDatabaseColumnName(column)){
	        usage("invalid column defined: must be alphanumeric");
	    }
	    vlog_options("column", column);
	    return column;
	}

	
	public static final String validate_database_fieldname (String field) {
	    if(field == null || field.trim().isEmpty()){
	        usage("field not defined");
	    }
	    field = field.trim();
	    if(! isDatabaseFieldName(field)){
	        usage("invalid field defined: must be alphanumeric");
	    }
	    vlog_options("field", field);
	    return field;
	}
	
	
	public static final String validate_database_query_select_show (String query, String name) {
	    name = name(name);
	    if(query == null || query.trim().isEmpty()){
	        usage(name + "query not defined");
	    }
	    query = query.trim();
	    if(! query.matches("^\\s*((?:SHOW|SELECT)\\s+(?!.*(?:INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TRUNCATE|;|--)).+)$")){
	        usage("invalid " + name + "query defined: may only be a SELECT or SHOW statement");
	    }
	    if(query.matches("insert|update|delete|create|drop|alter|truncate|;|--")){
	        usage("invalid " + name + "query defined: DML statement or suspect chars detected in query");
	    }
	    vlog_options(name + "query", query);
	    return query;
	}
	public static final String validate_database_query_select_show (String query) {
	    return validate_database_query_select_show(query, null);
	}
	
	
	public static final String validate_domain (String domain, String name) {
        name = name(name);
        if(domain == null || domain.trim().isEmpty()){
            usage(name + "domain not defined");
        }
        domain = domain.trim();
        if(! isDomain(domain)){
            usage("invalid " + name + "domain name defined ('" + domain + "')");
        }
        vlog_options(name + "domain", domain);
        return domain;
	}
	public static final String validate_domain (String domain) {
	    return validate_domain(domain, null);
	}
	
	
	public static final String validate_dirname (String dir, String name, Boolean noquit, Boolean novlog) {
	    name = name(name);
	    if(dir == null || dir.trim().isEmpty()){
	        usage(name + "directory not defined");
	    }
	    dir = dir.trim();
	    if(! isDirname(dir)){
	        if(noquit){
	            return null;
	        }
	        usage("invalid " + name + "directory (does not match regex criteria): '" + dir + "'");
	    }
	    if(! novlog){
	        vlog_options(name + "directory", dir);
	    }
	    return dir;
	}
	public static final String validate_dirname (String dir, String name, Boolean quit) {
		return validate_dirname(dir, name, quit, false);
	}
	public static final String validate_dirname (String dir, String name) {
		return validate_dirname(dir, name, false, false);
	}
	public static final String validate_dirname (String dir) {
		return validate_dirname(dir, null, false, false);
	}
	
	
	public static final String validate_directory (String dir, String name, Boolean noquit, Boolean novlog){
	    name = name(name);
	    if(noquit){
	        // XXX: call validate_filename with noquit
	        return validate_dirname(dir, name, true);
	    }
	    if(dir == null || dir.trim().isEmpty()){
	        usage(name + "directory not defined");
	    }
	    dir = dir.trim();
	    if(null == validate_dirname(dir, name, noquit, novlog)){
	        usage("invalid " + name + "directory (does not match regex criteria): '" + dir + "'");
	    }
	    return dir;
	}
	public static final String validate_directory (String dir, String name, Boolean noquit) {
	    return validate_directory(dir, name, noquit, false);
	}
	public static final String validate_directory (String dir, String name) {
	    return validate_directory(dir, name, false, false);
	}
	public static final String validate_directory (String dir, Boolean noquit) {
	    return validate_directory(dir, null, noquit, false);
	}
	public static final String validate_directory (String dir) {
	    return validate_directory(dir, null, false, false);
	}
	public static final String validate_dir (String dir, String name, Boolean noquit, Boolean novlog) {
		return validate_directory(dir, name, noquit, novlog);
	}
	public static final String validate_dir (String dir, String name, Boolean noquit) {
		return validate_directory(dir, name, noquit, false);
	}
	public static final String validate_dir (String dir, String name) {
		return validate_directory(dir, name, false, false);
	}
	public static final String validate_dir (String dir, Boolean noquit) {
		return validate_directory(dir, null, noquit, false);
	}
	public static final String validate_dir (String dir) {
		return validate_directory(dir, null, false, false);
	}
	
	
	public static final String validate_email (String email) {
	    if(email == null || email.trim().isEmpty()){
	        usage("email not defined");
	    }
	    email = email.trim();
	    if(! isEmail(email)){
	        usage("invalid email address defined: failed regex validation");
	    }
	    vlog_options("email", email);
	    return email;
	}
	
	
	public static final String validate_file (String filename, String name, Boolean noquit, Boolean novlog){
	    name = name(name);
	    validate_filename(filename, name, noquit, novlog);
	    File f = new File(filename);
	    if(!(f.exists() && ! f.isDirectory())){
	        usage(name + "file not found: " + filename);
	    }
	    return filename;
	}
	public static final String validate_file (String filename, String name, Boolean noquit) {
	    return validate_file(filename, name, noquit, false);
	}
	public static final String validate_file (String filename, String name) {
	    return validate_file(filename, name, false, false);
	}
	public static final String validate_file (String filename, Boolean noquit) {
	    return validate_file(filename, null, noquit, false);
	}
	public static final String validate_file (String filename) {
	    return validate_file(filename, null, false, false);
	}
	
	
	public static final String validate_filename (String filename, String name, Boolean noquit, Boolean novlog) {
	    name = name(name);
	    if(name.isEmpty()){
	        name = "filename ";
	    }
	    if(filename == null || filename.trim().isEmpty()){
	        usage(name + " not defined");
	    }
	    filename = filename.trim();
	    if(! isFilename(filename)){
	        if(noquit){
	            return null;
	        }
	        usage("invalid " + name + "(does not match regex criteria): '" + filename + "'");
	    }
	    if(! novlog){
	        vlog_options(name.trim(), filename);
	    }
	    return filename;
	}
	public static final String validate_filename (String filename, String name, Boolean noquit) {
	    return validate_filename(filename, name, noquit, false);
	}
	public static final String validate_filename (String filename, String name) {
	    return validate_filename(filename, name, false, false);
	}
	public static final String validate_filename (String filename, Boolean noquit) {
	    return validate_filename(filename, null, noquit, false);
	}
	public static final String validate_filename (String filename) {
	    return validate_filename(filename, null, false, false);
	}
	
	
	public static final String validate_fqdn (String fqdn, String name) {
	    name = name(name);
	    if(fqdn == null || fqdn.trim().isEmpty()){
	        usage(name + "FQDN not defined");
	    }
	    fqdn = fqdn.trim();
	    if(! isFqdn(fqdn)){
	        usage("invalid " + name + "FQDN defined");
	    }
	    vlog_options(name + "fqdn", fqdn);
	    return fqdn;
	}
	public static final String validate_fqdn (String fqdn) {
	    return validate_fqdn(fqdn, null);
	}
	
	/*
	public static final String[] validate_host_port_user_password(String host, String port, String user, String password){
	    return (validate_host(host), validate_port(port), validate_user(user), validate_password(password));
	}
	*/
	
	
	public static final String validate_host (String host, String name) {
	    name = name(name);
	    if(host == null || host.trim().isEmpty()){
	        usage(name + "host not defined");
	    }
	    host = host.trim();
	    if(! isHost(host)){
	        usage("invalid " + name + "host defined: not a valid hostname or IP address");
	    }
	    vlog_options(name + "host", host);
	    return host;
	}
	public static final String validate_host (String host) {
	    return validate_host(host, null);
	}
	
	private static final int parse_port (String port){
	    int port_int = -1;
        try{
            port_int = Integer.parseInt(port);
        } catch (Exception e) {
            usage("invalid port defined, not an integer");
        }
        if(!isPort(port)){
            usage("invalid port defined for hosts, must be between 1 and 65535");
        }
        return port_int;
	}
	
	public static final String[] validate_hosts (String[] hosts, int port) {
	    if(! isPort(port)){
	        usage("invalid port defined, integer must be between 1 and 65535");
	    }
	    hosts = uniq_array_ordered(hosts);
        if(hosts.length < 1){
            usage("hosts not defined");
        }
	    String[] hosts2 = hosts;
	    Pattern p = Pattern.compile(":(\\d+)$");
	    for(int i=0; i < hosts2.length; i++){
	        String node_port = null;
	        Matcher m = p.matcher(hosts2[i]);
	        if(m.find()){
	            node_port = m.group(1);
	            if(! isPort(node_port)){
	                usage("invalid port given for host " + Integer.toString(i+1));
	            }
	            hosts2[i] = hosts2[i].replaceAll(":\\d+$", "");
	        }
	        hosts2[i] = validate_host(hosts2[i]);
	        //hosts2[i] = validate_resolvable(hosts2[i]);
	        if(node_port == null){
	            node_port = Integer.toString(port);
	        }
	        hosts2[i] = hosts2[i] + ":" + node_port;
	        vlog_options("port", node_port);
	    }
	    return hosts2;
	}
    public static final String[] validate_hosts(String[] hosts, String port){
        // don't uniq here it's done in called validate_hosts method
        return validate_hosts(hosts, parse_port(port));
    }
    public static final ArrayList<String> validate_hosts(ArrayList<String> hosts, int port){
        // don't uniq here it's done in called validate_hosts method
        return array_to_arraylist(validate_hosts(arraylist_to_array(hosts), port));
    }
    public static final ArrayList<String> validate_hosts(ArrayList<String> hosts, String port){
        // don't uniq here it's done in called validate_hosts method
        return validate_hosts(hosts, parse_port(port));
    }
    public static final String validate_hosts (String hosts, int port) {
        if(hosts == null || hosts.trim().isEmpty()){
            usage("hosts not defined");
        }
        String[] hosts2 = validate_hosts(hosts.split("[,\\s]+"), port);
        String final_hosts = StringUtils.join(hosts2, ",");
        // vlogged in validate_nodeport_list
        //vlog_options("node list", final_hosts);
        return final_hosts;
    }
    public static final String validate_hosts (String hosts, String port) {
        return validate_hosts(hosts, parse_port(port));
    }
    
	
    public static final String validate_hostport (String hostport, String name, Boolean port_required, Boolean novlog) {
    	name = name(name);
    	if(hostport == null || hostport.trim().isEmpty()){
    		usage(name + "host:port not defined");
    	}
    	hostport = hostport.trim();
    	if(hostport.isEmpty()){
    	    usage(name + "host:port not defined");
    	}
    	String[] host_port = hostport.split(":");
    	if(host_port.length > 2){
    		usage("invalid " + name + "host:port supplied (too many colon separated components)");
    	}
    	if(! isHost(host_port[0])){
    		usage("invalid " + name + "host:port '" + hostport + "' defined: host portion '" + host_port[0] + "' is not a valid hostname or IP address");
    	}
    	if(host_port.length > 1){
    		if(isPort(host_port[1]) == null){
    			usage(String.format("invalid port '%s' defined for " + name + "host:port: must be a positive integer", host_port[1]));
    		}
    	} else if (port_required) {
    	    usage("port is required");
    	}
    	if(!novlog){
    	    vlog_options(name + "hostport", hostport);
    	}
    	return hostport;
    }
    public static final String validate_hostport (String host, String name, Boolean port_required) {
        return validate_hostport(host, name, port_required, false);
    }
    public static final String validate_hostport (String host, String name) {
        return validate_hostport(host, name, false, false);
    }
    public static final String validate_hostport (String host){
        return validate_hostport(host, null, false, false);
    }
    
    
    public static final String validate_hostname (String hostname, String name) {
        name = name(name);
        if(hostname == null || hostname.trim().isEmpty()){
            usage("hostname not defined");
        }
        hostname = hostname.trim();
        if(! isHostname(hostname)){
            usage("invalid " + name + "hostname '" + hostname + "' defined");
        }
        vlog_options(name + "hostname", hostname);
        return hostname;
    }
    public static final String validate_hostname (String hostname) {
        return validate_hostname(hostname, null);
    }
    
    
    public static final double validate_double (double d, String name, double  minVal, double maxVal) {
        name = require_name(name);
        if(d < minVal){
            usage("invalid " + name + " defined: cannot be lower than " + minVal);
        }n
        if(d > maxVal){
            usage("invalid " + name + " defined: cannot be greater than " + maxVal);
        }
        vlog_options(name, String.valueOf(d));
        return d;
    }
    public static final long validate_long (long l, String name, long minVal, long maxVal) {
        validate_double(l, name, minVal, maxVal);
        return l;
    }
    public static final int validate_int (int i, String name, int minVal, int maxVal) {
        validate_double(i, name, minVal, maxVal);
        return i;
    }
    public static final float validate_float (float f, String name, float minVal, float maxVal) {
        validate_double(f, name, minVal, maxVal);
        return f;
    }
    public static final double validate_double (String d, String name, double minVal, double maxVal) {
        name = require_name(name);
        double d_double = -1;
        try {
            d_double = Double.parseDouble(d);
        } catch (Exception e){
            usage("invalid " + name + " defined: must be numeric (double)");
        }
        // vlog_options done in validate_double
        validate_double(d_double, name, minVal, maxVal);
        return d_double;
    }
    public static final long validate_long (String l, String name, long minVal, long maxVal) {
        name = require_name(name);
        long l_long = -1;
        try {
            l_long = Long.parseLong(l);
        } catch (Exception e){
            usage("invalid " + name + " defined: must be numeric (long)");
        }
        // vlog_options done in validate_long
        validate_double(l_long, name, minVal, maxVal);
        return l_long;
    }
    public static final int validate_int (String i, String name, int minVal, int maxVal) throws Exception {
        name = require_name(name);
        int i_int = -1;
        try {
            i_int = Integer.parseInt(i);
        } catch (Exception e){
        	if(debug){
        		throw e;
        	}
            usage("invalid " + name + " defined: must be numeric (int)");
        }
        // vlog_options done in pass through to validate_long
        validate_double(i_int, name, minVal, maxVal);
        return i_int;
    }
    public static final float validate_float (String f, String name, float minVal, float maxVal) {
        name = require_name(name);
        float f_float = -1;
        try {
            f_float = Float.parseFloat(f);
        } catch (Exception e){
            usage("invalid " + name + " defined: must be numeric (float)");
        }
        // vlog_options done in pass through to validate_long
        validate_double(f_float, name, minVal, maxVal);
        return f_float;
    }
    
    
    public static final String validate_interface (String networkInterface) {
        if(networkInterface == null || networkInterface.trim().isEmpty()){
            usage("network interace not defined");
        }
        networkInterface = networkInterface.trim();
        if(! isInterface(networkInterface)){
            usage("invalid network interface defined: must be either eth<N>, bond<N> or lo<N>");
        }
        vlog_options("interface", networkInterface);
        return networkInterface;
    }
    
    
    public static final String validate_ip (String ip, String name) {
        name = name(name);
        if(ip == null || ip.trim().isEmpty()){
            usage(name + "IP not defined");
        }
        ip = ip.trim();
        if(! isIP(ip)){
            usage("invalid " + name + "IP defined");
        }
        vlog_options(name + "ip", ip);
        return ip;
    }
    public static final String validate_ip (String ip) {
        return validate_ip(ip, null);
    }
    
    
    public static final String validate_krb5_princ (String princ, String name) {
        name = name(name);
        if(princ == null || princ.trim().isEmpty()){
            usage(name + "krb5 principal not defined");
        }
        princ = princ.trim();
        if(! isKrb5Princ(princ)){
            usage("invalid " + name + "krb5 principal defined");
        }
        vlog_options(name + "krb5 principal", princ);
        return princ;
    }
    public static final String validate_krb5_princ (String princ) {
        return validate_krb5_princ(princ, null);
    }
    
    
    public static final String validate_krb5_realm (String realm, String name) {
        name = name(name);
        if(realm == null || realm.trim().isEmpty()){
            usage(name + "realm not defined");
        }
        realm = realm.trim();
        if(! isDomain(realm)){
            usage("invalid " + name + "realm defined");
        }
        vlog_options(name + "realm", realm);
        return realm;
    }
    public static final String validate_krb5_realm (String realm) {
        return validate_krb5_realm(realm, null);
    }
    
    
    public static final String validate_label (String label) {
        if(label == null || label.trim().isEmpty()){
            usage("label not defined");
        }
        label = label.trim();
        if(! isLabel(label)){
            usage("invalid label defined: must be an alphanumeric identifier");
        }
        vlog_options("label", label);
        return label;
    }
    
    
    public static final String validate_ldap_dn (String dn, String name) {
        name = name(name);
        if(dn == null || dn.trim().isEmpty()){
            usage("ldap " + name + "dn not defined");
        }
        dn = dn.trim();
        if(! isLdapDn(dn)){
            usage("invalid " + name + "ldap dn defined");
        }
        vlog_options("ldap " + name + "dn", dn);
        return dn;
    }
    public static final String validate_ldap_dn (String dn) {
        return validate_ldap_dn(dn, null);
    }

    
    public static final ArrayList<String> validate_node_list (ArrayList<String> nodes){
        ArrayList<String> final_nodes = new ArrayList<String>();
        nodes = uniq_arraylist_ordered(nodes);
        if(nodes.size() < 1){
            usage("node(s) not defined");
        }
        for(String node: nodes){
            //node = node.trim();
            for(String node2: node.split("[,\\s]+")){
                node2 = node2.trim();
                if(! isHost(node2)){
                    usage("invalid node name '" + node2 + "': must be hostname/FQDN or IP address");
                }
                final_nodes.add(node2);
            }
        }
        if(final_nodes.size() < 1){
            usage("node(s) not defined (empty nodes given)");
        }
        vlog_options("node list", final_nodes.toString());
        return final_nodes;
    }
    public static final String[] validate_node_list(String[] nodes){
        // don't uniq here it's done in called validate_node_list method
        return arraylist_to_array(validate_node_list(array_to_arraylist(nodes)));
    }
    public static final String validate_node_list (String nodelist) {
        if(nodelist == null || nodelist.trim().isEmpty()){
            usage("node(s) not defined");
        }
        String[] nodelist2 = validate_node_list(nodelist.split("[,\\s]+"));
        String final_nodes = StringUtils.join(nodelist2, ",");
        // vlogged in validate_node_list
        //vlog_options("node list", final_nodes);
        return final_nodes;
    }

    
    public static final ArrayList<String> validate_nodeport_list (ArrayList<String> nodes) {
        ArrayList<String> final_nodes = new ArrayList<String>();
        nodes = uniq_arraylist_ordered(nodes);
        if(nodes.size() < 1){
            usage("node(s) not defined");
        }
        for(String node: nodes){
            //node = node.trim();
            for(String node2: node.split("[,\\s]+")){
                //node2 = node2.trim();
                final_nodes.add( validate_hostport(node2) );
            }
        }
        vlog_options("nodeport list", final_nodes.toString());
        return final_nodes;
    }
    public static final String[] validate_nodeport_list(String[] nodes){
        // don't uniq here it's done in called validate_nodeport_list method
        return arraylist_to_array(validate_nodeport_list(array_to_arraylist(nodes)));
    }
    public static final String validate_nodeport_list (String nodelist) {
    	if(nodelist == null || nodelist.trim().isEmpty()){
    		usage("node(s) not defined");
    	}
    	String[] nodelist2 = validate_nodeport_list(nodelist.split("[,\\s]+"));
    	String final_nodes = StringUtils.join(nodelist2, ",");
    	// vlogged in validate_nodeport_list
    	//vlog_options("node list", final_nodes);
    	return final_nodes;
    }

    
    public static final String validate_nosql_key (String key, String name) {
        name = name(name);
        if(key == null || key.trim().isEmpty()){
            usage(name + "key not defined");
        }
        key = key.trim();
        if(! isNoSqlKey(key)){
            usage("invalid " + name + "key defined: may only contain characters: alphanumeric, commas, colons, underscores, pluses and dashes");
        }
        vlog_options(name + "key", key);
        return key;
    }
    public static final String validate_nosql_key (String key) {
        return validate_nosql_key(key, null);
    }
    
    
    public static final int validate_port (int port, String name) {
        name = name(name);
        if(! isPort(port)){
            usage("invalid " + name + "port defined");
        }
        vlog_options(name + "port", String.valueOf(port));
        return port;
    }
    public static final String validate_port (String port, String name){
        int port_int = -1;
        try {
            port_int = Integer.parseInt(port);
        } catch (Exception e){
            usage("invalid " + name + "port specified: must be numeric");
        }
        return String.valueOf(validate_port(port_int, name));
    }
    public static final int validate_port (int port) {
        return validate_port(port, null);
    }
    public static final String validate_port (String port) {
        return validate_port(port, null);
    }
    
    
    public static final String validate_process_name (String process, String name) {
        name = name(name);
        if(process == null || process.trim().isEmpty()){
            usage(name + "process name not defined");
        }
        process = process.trim();
        if(! isProcessName(process)){
            usage("invalid" + name + "process name defined:");
        }
        return process;
    }
    public static final String validate_process_name (String process) {
        return validate_process_name(process, null);
    }
    
    
    public static final String validate_program_path (String path, String name, String regex) {
        name = require_name(name).trim();
        if(path == null || path.trim().isEmpty()){
            usage(name + " path not defined");
        }
        path = path.trim();
        if(! path.matches("^[./]")){
            path = which(path);
            if(path == null){
                usage(name + " program not found in $PATH (" + System.getenv("PATH") + ")");
            }
        }
        if(regex == null || regex.trim().isEmpty()){
            regex = name;
        }
        if(validate_regex(regex, "program path regex", true) == null){
            arg_error("invalid regex given to validate_program_path()");
        }
        if(validate_filename(path, null, false, true) == null){
            usage("invalid path given for " + name + ", failed filename regex");
        }
        if(! path.matches("(?:^|.*/)" + regex + "$")){
           usage("invalid path given for " + name + ", is not a path to the " + name + " command");
        }
        File f = new File(path);
        if(!(f.exists() && ! f.isDirectory())){
            usage(path + " not found");
        }
        if(!f.canExecute()){
            usage(path + " not executable");
        }
        vlog_options(name + " program path", path);
        return path;
    }
    public static final String validate_program_path (String path, String name) {
        return validate_program_path(path, name, null);
    }
    
    
    public static final String validate_regex (String regex, String name, Boolean noquit, Boolean posix) {
        name = name(name);
        // intentionally not trimming
        if(regex == null || regex.isEmpty()){
            if(noquit){
                return null;
            } else {
                usage(name + "regex not defined");
            }
        }
        if(posix){
            if(regex.matches("$\\(|`")){
                if(noquit){
                    return null;
                }
                usage("invalid " + name + "posix regex supplied: contains sub shell metachars ( $( / ` ) that would be dangerous to pass to shell");
            }
            // TODO: cmd("egrep '$regex' < /dev/null") and check for any output signifying error with the regex
        } else {
            try {
                "".matches(regex);
            } catch (Exception e) {
                usage("invalid " + name + "regex defined");
            }
        }
        if(! noquit){
            vlog_options(name + "regex", regex);
        }
        return regex;
    }
    public static final String validate_regex (String regex, String name, Boolean noquit) {
        return validate_regex(regex, name, noquit, false);
    }
    public static final String validate_regex (String regex, String name) {
        return validate_regex(regex, name, false, false);
    }
    
    
    public static final String validate_user (String user, String name) {
        name = name(name);
        if(user == null || user.trim().isEmpty()){
            usage(name + "user not defined");
        }
        user = user.trim();
        if(! isUser(user)){
            usage("invalid " + name + "username defined: must be alphanumeric");
        }
        vlog_options(name + "user", user);
        return user;
    }
    public static final String validate_user (String user) {
        return validate_user(user, null);
    }
    
    
    public static final String validate_user_exists (String user, String name) {
        user = validate_user(user, name);
        name = name(name);
        if(! user_exists(user)){
            usage("invalid " + name + "user defined, not found on local system");
        }
        return user;
    }
    
    
    public static final String validate_password (String password, String name, Boolean allow_all) {
        name = name(name);
        if(password == null || password.trim().isEmpty()){
            usage(name + "password not defined");
        }
        if(allow_all){
            return password;
        }
        if(!password.matches("^[^\"'`]+$")){
            usage("invalid " + name + "password defined: may not contain quotes or backticks");
        }
        if(password.matches(".*$\\(.*")){
            usage("invalid " + name + "password defined: may not conatina $( as this is a subshell escape and could be dangerous to pass through to programs on the command line");
        }
        return password;
    }
    public static final String validate_password (String password, String name) {
        return validate_password(password, name, false);
    }
    public static final String validate_password (String password) {
        return validate_password(password, null, false);
    }
    
    
    public static final String validate_resolvable (String host, String name) {
        name = name(name);
        if(host == null || host.trim().isEmpty()){
            arg_error(name + "host not defined");
        }
        host = host.trim();
        String ip = resolve_ip(host);
        if(ip == null){
            quit("CRITICAL", "failed to resolve " + name + "host '" + host + "'");
        }
        return ip;
    }
    public static final String validate_resolvable (String host) {
        return validate_resolvable(host, null);
    }
    
    
    public static final String validate_units (String units, String name) {
        name = name(name);
        if(units == null || units.trim().isEmpty()){
            usage(name + "units not defined");
        }
        units = units.trim();
        if(! isNagiosUnit(units)){
            usage("invalid " + name + "units '" + units + "' defined, must be one of: " + valid_units.toString());
        }
        vlog_options(name + "units", units);
        return units;
    }
    public static final String validate_units (String units) {
        return validate_units(units, null);
    }
    
    
    public static final String validate_url (String url, String name) {
        name = name(name);
        if(url == null || url.trim().isEmpty()){
            usage(name + "url not defined");
        }
        url = url.trim();
        if(!url.contains("://")){
            url = "http://" + url;
        }
        if(! isUrl(url)){
            usage("invalid " + name + "url defined: '" + url + "'");
        }
        vlog_options(name + "url", url);
        return url;
    }
    public static final String validate_url (String url) {
        return validate_url(url, null);
    }
    
    
    public static final String validate_url_path_suffix (String url, String name) {
        name = name(name);
        if(url == null || url.trim().isEmpty()){
           usage(name + "url not defined");
        }
        url = url.trim();
        if(! isUrlPathSuffix(url)){
            usage("invalid " + name + "url defined: '" + url + "'");
        }
        return url;
    }
    public static final String validate_url_path_suffix (String url) {
    	return validate_url_path_suffix(url, null);
    }
    
    public static final String getVersion () {
    	return utils_version;
    }
    
    public static final void version () {
        println("Hari Sekhon Java Utils version " + getVersion());
    }
    
    public static final String which (String bin, Boolean quit) {
        if(bin == null || bin.trim().isEmpty()){
            arg_error("no bin passed to which()");
        }
        // TODO: should probably consider switching this to os path sep instead of unix biased /
        if(bin.matches("^(?:/|\\./).*")){
            File f = new File(bin);
            if(f.exists() && ! f.isDirectory()){
                if(f.canExecute()){
                   return bin; 
                } else {
                    if(quit){
                        quit("UNKNOWN", "'" + bin + "' is not executable!");
                    }
                }
            } else {
                if(quit){
                    quit("UNKNOWN", "couldn't find executable '" + bin + "'");
                }
            }
        } else {
            for(String path: System.getenv("PATH").split(":")){
                String fullpath = path + "/" + bin;
                File f = new File(fullpath);
                if((f.exists() && ! f.isDirectory())){
                    if(! f.canExecute()){
                        if(quit){
                            quit("UNKNOWN", "'" + bin + "' is not executable!");
                        }
                    }
                    return fullpath;
                }
            }
            if(quit){
                quit("UNKNOWN", "couldn't find '" + bin + "' in PATH (" + System.getenv("PATH") + ")");
            }
        }
        return null;
    }
    public static final String which (String bin) {
        return which(bin, false);
    }
    
    // ===================================================================== //
    //
    //                            L o g g i n g
    //
    // ===================================================================== //
    
    public static final int getVerbose(){
    	return verbose;
    }
    
    public static final void setVerbose (int v) {
    	if(v >= 0){
    		verbose = v;
    	} else {
    		arg_error(String.format("invalid verbosity level '%s' passed to setVerbose()", v));
    	}
    }
    
    // TODO: add Log4j here
    
    public static final void vlog (String str) {
        if(str == null){
            str = "<null!>";
        }
        if(getVerbose() >= 1){
            println(str);
        }
    }

    public static final void vlog2 (String str) {
        if(getVerbose() >= 2){
            println(str);
        }
    }
    
    public static final void vlog3 (String str) {
        if(getVerbose() >= 3){
            println(str);
        }
    }
    
    public static final void vlog_options (String option, String value) {
        if(option == null){
            option = "<null!>";
        }
        if(value == null){
            value = "<null!>";
        }
    	System.out.printf("%-25s %s\n", option, value);
    }
    
    public static final void vlog_options_bool (String option, Boolean value) {
    	vlog_options(option, value.toString());
    }
}

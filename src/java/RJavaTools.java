import java.lang.reflect.Method ;
import java.lang.reflect.Constructor ;

/** 
 * Tools used internally by rJava.
 * 
 * The method lookup code is heavily based on ReflectionTools 
 * by Romain Francois <francoisromain@free.fr> licensed under GPL v2 or higher.
 */
public class RJavaTools {
	
	/**
	 * Attempts to find the best-matching constructor of the class
	 * o_clazz with the parameter types arg_clazz
	 * 
	 * @param o_clazz Class to look for a constructor
	 * @param arg_clazz parameter types
	 * 
	 * @return <code>null</code> if no constructor is found, or the constructor
	 *
	 */
	public static Constructor getConstructor( Class o_clazz, Class[] arg_clazz){
		
		if (o_clazz == null)
			return null; 

		/* if there is no argument, try to find a direct match */
		if (arg_clazz == null || arg_clazz.length == 0) {
			try {
				Constructor c = o_clazz.getConstructor( (Class[] )null );
				if (c != null)
					return c ;
			} catch (SecurityException e) {
			} catch (NoSuchMethodException e) {
			}
			
			// we can bail out here because if there was no match, 
			// no constructor has zero arguments so further search is futile
			return null;
		}

		/* try to find an exact match */
		Constructor cons;
		try {
			cons = o_clazz.getConstructor(arg_clazz);
			if (cons != null)
				return cons ;
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
		}
		
		/* ok, no exact match was found - we have to walk through all methods */
		cons = null;
		Constructor[] candidates = o_clazz.getConstructors();
		for (int k = 0; k < candidates.length; k++) {
			Constructor c = candidates[k];
			Class[] param_clazz = c.getParameterTypes();
			if (arg_clazz.length != param_clazz.length) // number of parameters must match
				continue;
			int n = arg_clazz.length;
			boolean ok = true; 
			for (int i = 0; i < n; i++) {
				if (arg_clazz[i] != null && !param_clazz[i].isAssignableFrom(arg_clazz[i])) {
					ok = false; 
					break;
				}
			}
			
			// it must be the only match so far or more specific than the current match
			if (ok && (cons == null || isMoreSpecific(c, cons)))
				cons = c; 
		}
		return cons; 
		
	}
	
	
	/**
	 * Attempts to find the best-matching method of the class <code>o_clazz</code> with the method name <code>name</code> and arguments types defined by <code>arg_clazz</code>.
	 * The lookup is performed by finding the most specific methods that matches the supplied arguments (see also {@link #isMoreSpecific}).
	 *
	 * @param o_clazz class in which to look for the method
	 * @param name method name
	 * @param arg_clazz an array of classes defining the types of arguments
	 *
	 * @return <code>null</code> if no matching method could be found or the best matching method.
	 *
	 * @author Romain Francois <francoisromain@free.fr>
	 */
	public static Method getMethod(Class o_clazz, String name, Class[] arg_clazz) {
		if (o_clazz == null)
			return null; 

		/* if there is no argument, try to find a direct match */
		if (arg_clazz == null || arg_clazz.length == 0) {
			try {
				Method m = o_clazz.getMethod(name, (Class[])null);
				if (m != null)
					return m ;
			} catch (SecurityException e) {
			} catch (NoSuchMethodException e) {
			}
			// we can bail out here because if there was no match, no method has zero arguments so further search is futile
			return null;
		}

		/* try to find an exact match */
		Method met;
		try {
			met = o_clazz.getMethod(name, arg_clazz);
			if (met != null)
				return met;
		} catch (SecurityException e) {
		} catch (NoSuchMethodException e) {
		}
		
		/* ok, no exact match was found - we have to walk through all methods */
		met = null;
		Method[] ml = o_clazz.getMethods();
		for (int k = 0; k < ml.length; k++) {
			Method m = ml[k];
			if (!m.getName().equals(name)) // the name must match
				continue; 
			Class[] param_clazz = m.getParameterTypes();
			if (arg_clazz.length != param_clazz.length) // number of parameters must match
				continue;
			int n = arg_clazz.length;
			boolean ok = true; 
			for (int i = 0; i < n; i++) {
				if (arg_clazz[i] != null && !param_clazz[i].isAssignableFrom(arg_clazz[i])) {
					ok = false; 
					break;
				}
			}
			if (ok && (met == null || isMoreSpecific(m, met))) // it must be the only match so far or more specific than the current match
				met = m; 
		}
		return met; 
	}

	/** 
	 * Returns <code>true</code> if <code>m1</code> is more specific than <code>m2</code>. 
	 * The measure used is described in the isMoreSpecific( Class[], Class[] ) method
	 *
	 * @param m1 method to compare
	 * @param m2 method to compare 
	 *
	 * @return <code>true</code> if <code>m1</code> is more specific (in arguments) than <code>m2</code>.
	 */
	private static boolean isMoreSpecific(Method m1, Method m2) {
		Class[] m1_param_clazz = m1.getParameterTypes();
		Class[] m2_param_clazz = m2.getParameterTypes();
		return isMoreSpecific( m1_param_clazz, m2_param_clazz ); 
	}
	
	/** 
	 * Returns <code>true</code> if <code>cons1</code> is more specific than <code>cons2</code>. 
	 * The measure used is described in the isMoreSpecific( Class[], Class[] ) method
	 *
	 * @param cons1 constructor to compare
	 * @param cons2 constructor to compare 
	 * 
	 * @return <code>true</code> if <code>cons1</code> is more specific (in arguments) than <code>cons2</code>.
	 */
	private static boolean isMoreSpecific(Constructor cons1, Constructor cons2) {
		Class[] cons1_param_clazz = cons1.getParameterTypes();
		Class[] cons2_param_clazz = cons2.getParameterTypes();
		return isMoreSpecific( cons1_param_clazz, cons2_param_clazz ); 
	}
	 
	/**
	 * Returns <code>true</code> if <code>c1</code> is more specific than <code>c2</code>. 
	 *
	 * The measure used is the sum of more specific arguments minus the sum of less specific arguments 
	 * which in total must be positive to qualify as more specific. 
	 * (More specific means the argument is a subclass of the other argument). 
	 *
	 * Both set of classes must have signatures fully compatible in the arguments 
	 * (more precisely incompatible arguments are ignored in the comparison).
	 *
	 * @param c1 set of classes to compare
	 * @param c2 set of classes to compare
   */
	private static boolean isMoreSpecific( Class[] c1, Class[] c2){
	 	int n = c1.length ;
		int res = 0; 
		for (int i = 0; i < n; i++)
			if( c1[i] != c2[i]) {
				if( c1[i].isAssignableFrom(c2[i]))
					res--;
				else if( c2[i].isAssignableFrom(c2[i]) )
					res++;
			}
		return res > 0;
	}
	
	
}
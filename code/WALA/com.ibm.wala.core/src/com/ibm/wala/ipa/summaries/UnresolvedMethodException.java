package com.ibm.wala.ipa.summaries;

// FIXME this only exists to exit the evaluation of a lambda method when the target class has
//  been excluded. this shouldn't need an exception
public class UnresolvedMethodException extends RuntimeException {
}

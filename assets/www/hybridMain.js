/**
 * hybrid namespace
 * Illustrates Event-Pooling concepts
 */
var hybrid = hybrid || {};

// IIFE parameter: 'ns' namespace arg; 'undefined' defaults to undefined
;(function ( ns, undefined ) {
  // Event Pooling nested namespace
  ns.eventUtils = {
    // register space-separated event names to a callback
    subscribeEvents: function(eventNames, callback) {
      $(document).on(eventNames, callback);
	},
	// trigger an event
	triggerEvent: function(eventName, data, delay) {
      setTimeout(function() {
        $(document).trigger(eventName, data);
      }, (delay || 0));
	}
  };

  // convenience namespace to use for functions in main.js
  var mainjs = window;

  // general function to query native "People" address book
  ns.queryNativeContacts = function(evt, data) {
	switch(evt.type) {
	  case 'GET_NATIVE_CONTACTS':
	    // data is expected to have a property 'inType'
	    mainjs.doFetchContacts(data.inType);
	    break;
	  default:
	    // default handler if any
	}
  };

  // subscribe to event to get all native Contacts;
  //  call with triggerEvent('GET_NATIVE_CONTACTS', data, 0);
  //  data is expected to have a property 'inType'
  // can subscribe to more than one event, use space-separated event names
  ns.eventUtils.subscribeEvents('GET_NATIVE_CONTACTS', function(evt, data) {
    ns.queryNativeContacts.apply(null, [evt, data]);
  });

})(hybrid); // 'hybrid' namespace

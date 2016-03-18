(function () {
    var test = "{{$title}} ";

    var fun = function () {
        return "World";
    };

    console.log(test + fun());
})();
package bt.example;

import bt.example.cli.CliWrapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.nhl.bootique.BQCoreModule;
import com.nhl.bootique.Bootique;

public class Examples implements Module {

    public static void main(String[] args) {
        Bootique.app(args).module(Examples.class).run();
    }

    @Override
    public void configure(Binder binder) {
        BQCoreModule.contributeCommands(binder)
			.addBinding().to(CliWrapper.class);
    }
}
